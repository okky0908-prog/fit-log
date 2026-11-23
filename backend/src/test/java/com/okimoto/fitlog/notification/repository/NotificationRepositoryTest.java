package com.okimoto.fitlog.notification.repository;

import static com.okimoto.fitlog.jooq.Tables.NOTIFICATIONS;
import static com.okimoto.fitlog.jooq.Tables.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.okimoto.fitlog.common.calc.Cursor;
import com.okimoto.fitlog.support.TestcontainersConfiguration;
import com.okimoto.fitlog.support.WorkoutFixture;
import com.okimoto.fitlog.user.repository.NewUser;
import com.okimoto.fitlog.user.repository.UserRepository;
import com.okimoto.fitlog.workout.repository.WorkoutRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * 通知の読み書き（docs/database.md「notifications」、docs/test-plan.md 4.1・5.9）。本物の DB で確かめる（テストごとにロールバックする）
 */
@JooqTest
@Import({
  TestcontainersConfiguration.class,
  NotificationRepository.class,
  UserRepository.class,
  WorkoutRepository.class
})
@ActiveProfiles("test")
class NotificationRepositoryTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-23T10:00:00+09:00");

  @Autowired private NotificationRepository notificationRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private WorkoutRepository workoutRepository;
  @Autowired private DSLContext dsl;

  private long me;
  private long sato;
  private long suzuki;
  private WorkoutFixture workouts;

  @BeforeEach
  void setUp() {
    me = userRepository.insert(user("me_user", "自分")).getId();
    sato = userRepository.insert(user("sato", "佐藤花子")).getId();
    suzuki = userRepository.insert(user("suzuki", "鈴木一郎")).getId();
    workouts = new WorkoutFixture(dsl, workoutRepository);
  }

  @Test
  void 自分への通知を新しい順に_同じ日時ならIDの大きい順に_件数まで返す() {
    long myWorkout = workouts.of(me).body("脚の日\n#脚トレ").save();
    long oldest = follow(sato, me, NOW.minusHours(2));
    long sameTimeFirst = like(sato, me, myWorkout, NOW.minusHours(1));
    long sameTimeSecond = like(suzuki, me, myWorkout, NOW.minusHours(1));
    long newest = follow(suzuki, me, NOW);
    follow(me, sato, NOW.plusHours(1)); // ほかの人への通知

    List<NotificationRow> page = notificationRepository.findPage(me, null, 3);

    assertThat(page)
        .extracting(NotificationRow::id)
        .containsExactly(newest, sameTimeSecond, sameTimeFirst);
    NotificationRow like = page.get(1);
    assertThat(like.type()).isEqualTo("like");
    assertThat(like.actorUsername()).isEqualTo("suzuki");
    assertThat(like.actorDisplayName()).isEqualTo("鈴木一郎");
    assertThat(like.actorPrivate()).isFalse();
    assertThat(like.workoutId()).isEqualTo(myWorkout);
    assertThat(like.workoutBody()).isEqualTo("脚の日\n#脚トレ");
    assertThat(like.readAt()).isNull();
    assertThat(page.get(0).workoutId()).isNull();
    assertThat(page.get(0).workoutBody()).isNull();

    // 続きは、最後の通知の位置より後（古い）もの
    NotificationRow last = page.get(2);
    assertThat(
            notificationRepository.findPage(
                me, new Cursor.Position(last.createdAt(), last.id()), 3))
        .extracting(NotificationRow::id)
        .containsExactly(oldest);
  }

  @Test
  void 見られなくなった投稿についての通知は_一覧に出さない() {
    // 自分の投稿（どの公開範囲でも、自分は見られる）
    long myPrivate = workouts.of(me).visibility("private").save();
    // 他人の投稿（返信の通知など）。自分のみ・鍵アカウントの公開は見られない
    long othersPublic = workouts.of(sato).save();
    long othersPrivate = workouts.of(sato).visibility("private").save();
    dsl.update(USERS).set(USERS.IS_PRIVATE, true).where(USERS.ID.eq(suzuki)).execute();
    long lockedPublic = workouts.of(suzuki).save();
    long onMine = like(sato, me, myPrivate, NOW);
    long onOthersPublic = like(sato, me, othersPublic, NOW);
    like(sato, me, othersPrivate, NOW);
    like(suzuki, me, lockedPublic, NOW);

    assertThat(notificationRepository.findPage(me, null, 20))
        .extracting(NotificationRow::id)
        .containsExactlyInAnyOrder(onMine, onOthersPublic);
  }

  @Test
  void 未読の数は_自分の見られる未読の通知だけを数え_上限で数えるのをやめる() {
    long othersPrivate = workouts.of(sato).visibility("private").save();
    follow(sato, me, NOW);
    follow(suzuki, me, NOW);
    long read = follow(sato, me, NOW);
    notificationRepository.markRead(me, List.of(read), NOW);
    like(sato, me, othersPrivate, NOW); // 見られない
    follow(me, sato, NOW); // ほかの人への通知

    assertThat(notificationRepository.countUnread(me, 100)).isEqualTo(2);
    assertThat(notificationRepository.countUnread(me, 1)).isEqualTo(1);
  }

  @Test
  void 既読にするのは_自分の未読の通知だけ_既読の日時は変えない() {
    long unread = follow(sato, me, NOW);
    long alreadyRead = follow(suzuki, me, NOW);
    notificationRepository.markRead(me, List.of(alreadyRead), NOW.minusDays(1));
    long others = follow(me, sato, NOW);

    int updated =
        notificationRepository.markRead(me, List.of(unread, alreadyRead, others, 999_999L), NOW);

    assertThat(updated).isEqualTo(1);
    assertThat(readAt(unread)).isAtSameInstantAs(NOW);
    assertThat(readAt(alreadyRead)).isAtSameInstantAs(NOW.minusDays(1));
    assertThat(readAt(others)).isNull();
  }

  @Test
  void 自分の操作で自分への通知は_DBでも作れない() {
    assertThatThrownBy(() -> follow(me, me, NOW))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /** 1回の失敗でトランザクションが中断されるので、ケースごとに分ける */
  @ParameterizedTest(name = "[{index}] {0}")
  @CsvSource({
    "ナイストレーニングに投稿がない, like, true, false",
    "フォローに投稿がある, follow, true, true",
    "目標の達成に操作した人がいる, goal_achieved, true, false",
    "知らない種類, unknown, true, false",
  })
  void 種類に合わない列の値は_DBでも入れられない(String name, String type, boolean withActor, boolean withWorkout) {
    Long workout = withWorkout ? workouts.of(me).save() : null;

    assertThatThrownBy(() -> insert(type, withActor ? sato : null, me, workout))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 投稿を消すと_その通知も消える() {
    long workout = workouts.of(me).save();
    long notification = like(sato, me, workout, NOW);
    long kept = follow(sato, me, NOW);

    workoutRepository.delete(workout);

    assertThat(dsl.fetchExists(NOTIFICATIONS, NOTIFICATIONS.ID.eq(notification))).isFalse();
    assertThat(dsl.fetchExists(NOTIFICATIONS, NOTIFICATIONS.ID.eq(kept))).isTrue();
  }

  private long like(long actor, long recipient, long workoutId, OffsetDateTime at) {
    return notificationRepository.insert(
        new NewNotification(recipient, actor, "like", workoutId, at));
  }

  private long follow(long actor, long recipient, OffsetDateTime at) {
    return notificationRepository.insert(new NewNotification(recipient, actor, "follow", null, at));
  }

  private long insert(String type, Long actor, long recipient, Long workoutId) {
    return notificationRepository.insert(
        new NewNotification(recipient, actor, type, workoutId, NOW));
  }

  private OffsetDateTime readAt(long id) {
    return dsl.fetchValue(NOTIFICATIONS.READ_AT, NOTIFICATIONS.ID.eq(id));
  }

  private static NewUser user(String username, String displayName) {
    return new NewUser(username, displayName, username + "@example.com", "hash", NOW);
  }
}
