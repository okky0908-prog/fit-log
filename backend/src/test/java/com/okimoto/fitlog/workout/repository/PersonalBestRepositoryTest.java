package com.okimoto.fitlog.workout.repository;

import static com.okimoto.fitlog.jooq.Tables.EXERCISES;
import static org.assertj.core.api.Assertions.assertThat;

import com.okimoto.fitlog.support.TestcontainersConfiguration;
import com.okimoto.fitlog.support.WorkoutFixture;
import com.okimoto.fitlog.user.repository.NewUser;
import com.okimoto.fitlog.user.repository.UserRepository;
import com.okimoto.fitlog.workout.calc.PersonalBest;
import com.okimoto.fitlog.workout.calc.StoredSet;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 自己ベスト更新の印の SQL（docs/test-plan.md 5.3 のデシジョンテーブル）。印を付ける種目・セットを Java で選ぶ部分（ケース 12・13 の後半）は
 * PersonalBestRulesTest で確かめる。
 *
 * <p>比べるのは同じ人の記録だけなので、テストごとに新しいユーザーを作る。テストごとにロールバックする
 */
@JooqTest
@Import({
  TestcontainersConfiguration.class,
  PersonalBestRepository.class,
  WorkoutRepository.class,
  UserRepository.class
})
@ActiveProfiles("test")
class PersonalBestRepositoryTest {

  private static final LocalDate DAY = LocalDate.of(2026, 11, 20);
  private static final OffsetDateTime AT = OffsetDateTime.parse("2026-11-20T19:00:00+09:00");

  @Autowired private PersonalBestRepository repository;
  @Autowired private WorkoutRepository workoutRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private DSLContext dsl;

  private WorkoutFixture workouts;
  private long me;

  @BeforeEach
  void setUp() {
    workouts = new WorkoutFixture(dsl, workoutRepository);
    me = user("pb_me");
  }

  // ケース 1
  @Test
  void 初めての種目には付けない() {
    long id = bench(me, DAY, AT, "60");

    assertThat(repository.findUpdated(List.of(id))).isEmpty();
  }

  // ケース 2
  @Test
  void 前の最大と同じなら付けない() {
    bench(me, DAY.minusDays(1), AT, "60");
    long id = save(me, DAY, AT, "ベンチプレス", wr("60", 8));

    assertThat(repository.findUpdated(List.of(id))).isEmpty();
  }

  // ケース 3：135lb を kg に直した値（61.235kg）をわずかに超える
  @Test
  void 前の最大をわずかに超えれば付ける() {
    bench(me, DAY.minusDays(1), AT, "61.235");
    long id = bench(me, DAY, AT, "61.250");

    assertThat(repository.findUpdated(List.of(id))).containsExactly(pb(id, "61.250"));
  }

  // ケース 4
  @Test
  void 重いセットが0回だけなら付けない() {
    bench(me, DAY.minusDays(1), AT, "60");
    long id = save(me, DAY, AT, "ベンチプレス", wr("80", 0), wr("55", 5));

    assertThat(repository.findUpdated(List.of(id))).isEmpty();
  }

  // ケース 5
  @Test
  void 前の記録の0回のセットは数えない() {
    save(me, DAY.minusDays(1), AT, "ベンチプレス", wr("80", 0), wr("50", 5));
    long id = bench(me, DAY, AT, "60");

    assertThat(repository.findUpdated(List.of(id))).containsExactly(pb(id, "60"));
  }

  // ケース 6
  @Test
  void 同じ実施日に先に投稿した記録を超えれば付ける() {
    bench(me, DAY, AT, "60");
    long id = bench(me, DAY, AT.plusHours(1), "70");

    assertThat(repository.findUpdated(List.of(id))).containsExactly(pb(id, "70"));
  }

  // ケース 7
  @Test
  void 同じ実施日にあとで投稿した記録は前に入らない() {
    long earlier = bench(me, DAY, AT, "60");
    bench(me, DAY.minusDays(1), AT, "50");
    long later = bench(me, DAY, AT.plusHours(1), "70");

    assertThat(repository.findUpdated(List.of(earlier, later)))
        .containsExactlyInAnyOrder(pb(earlier, "60"), pb(later, "70"));
  }

  // 実施日も投稿日時も同じなら、ID の順で前・後を決める
  @Test
  void 実施日と投稿日時が同じなら_IDが小さい方が前() {
    bench(me, DAY, AT, "60");
    long id = bench(me, DAY, AT, "70");

    assertThat(repository.findUpdated(List.of(id))).containsExactly(pb(id, "70"));
  }

  // ケース 8
  @Test
  void 過去の日付をあとから登録しても_あとの記録の印は付いたまま() {
    bench(me, LocalDate.of(2026, 11, 10), AT, "60");
    long nov12 = bench(me, LocalDate.of(2026, 11, 12), AT, "70");
    long nov11 = bench(me, LocalDate.of(2026, 11, 11), AT.plusDays(5), "65");

    assertThat(repository.findUpdated(List.of(nov11, nov12)))
        .containsExactlyInAnyOrder(pb(nov11, "65"), pb(nov12, "70"));
  }

  // ケース 9
  @Test
  void 前の記録を削除すると付く() {
    long heavy = bench(me, DAY.minusDays(2), AT, "70");
    bench(me, DAY.minusDays(1), AT, "60");
    long id = bench(me, DAY, AT, "65");
    assertThat(repository.findUpdated(List.of(id))).isEmpty();

    workoutRepository.delete(heavy);

    assertThat(repository.findUpdated(List.of(id))).containsExactly(pb(id, "65"));
  }

  // ケース 10
  @Test
  void 自分のみの記録とも比べる() {
    workouts
        .of(me)
        .performedOn(DAY.minusDays(1))
        .visibility("private")
        .weightReps("ベンチプレス", 70, 5, 1)
        .save();
    long id = bench(me, DAY, AT, "65");

    assertThat(repository.findUpdated(List.of(id))).isEmpty();
  }

  // ケース 11
  @Test
  void 他人の記録とは比べない() {
    long sato = user("pb_sato");
    bench(sato, DAY.minusDays(1), AT, "100");
    bench(me, DAY.minusDays(1), AT, "50");
    long id = bench(me, DAY, AT, "60");

    assertThat(repository.findUpdated(List.of(id))).containsExactly(pb(id, "60"));
  }

  // 種目ごとに比べる（ほかの種目の重さとは比べない）
  @Test
  void 違う種目の重さとは比べない() {
    save(me, DAY.minusDays(1), AT, "スクワット", wr("100", 5));
    bench(me, DAY.minusDays(1), AT, "50");
    long id = bench(me, DAY, AT, "60");

    assertThat(repository.findUpdated(List.of(id))).containsExactly(pb(id, "60"));
  }

  // ケース 12・13：同じ種目が2回あれば、まとめた最大重量で1件
  @Test
  void 同じ記録に同じ種目が2回なら_まとめた最大重量で判定する() {
    bench(me, DAY.minusDays(1), AT, "60");
    long id =
        workouts
            .of(me)
            .performedOn(DAY)
            .createdAt(AT)
            .weightReps("ベンチプレス", 65, 5, 1)
            .weightReps("ベンチプレス", 70, 5, 1)
            .save();

    assertThat(repository.findUpdated(List.of(id))).containsExactly(pb(id, "70"));
  }

  // ケース 14
  @Test
  void 重量と回数でない種目には付かない() {
    workouts
        .of(me)
        .performedOn(DAY.minusDays(1))
        .reps("腕立て伏せ", 10, 1)
        .timeDistance("ランニング", 1800, 5.0)
        .save();
    long id =
        workouts
            .of(me)
            .performedOn(DAY)
            .reps("腕立て伏せ", 30, 1)
            .timeDistance("ランニング", 3600, 10.0)
            .save();

    assertThat(repository.findUpdated(List.of(id))).isEmpty();
  }

  // 1ページの記録をまとめて求める。記録ごと・種目ごとに1件
  @Test
  void いくつかの記録の_更新した種目だけを返す() {
    save(me, DAY.minusDays(2), AT, "ベンチプレス", wr("50", 5));
    save(me, DAY.minusDays(2), AT, "スクワット", wr("80", 5));
    long both =
        workouts
            .of(me)
            .performedOn(DAY.minusDays(1))
            .createdAt(AT)
            .weightReps("ベンチプレス", 60, 5, 1)
            .weightReps("スクワット", 90, 5, 1)
            .save();
    long none = bench(me, DAY, AT, "55");

    assertThat(repository.findUpdated(List.of(both, none)))
        .containsExactlyInAnyOrder(pb(both, "60"), pb(both, "スクワット", "90"));
  }

  @Test
  void 記録がなければ空() {
    assertThat(repository.findUpdated(List.of())).isEmpty();
  }

  private long bench(long userId, LocalDate performedOn, OffsetDateTime createdAt, String kg) {
    return save(userId, performedOn, createdAt, "ベンチプレス", wr(kg, 5));
  }

  private long save(
      long userId,
      LocalDate performedOn,
      OffsetDateTime createdAt,
      String exercise,
      StoredSet... sets) {
    return workouts
        .of(userId)
        .performedOn(performedOn)
        .createdAt(createdAt)
        .exercise(exercise, List.of(sets))
        .save();
  }

  private PersonalBest pb(long workoutId, String kg) {
    return pb(workoutId, "ベンチプレス", kg);
  }

  private PersonalBest pb(long workoutId, String exercise, String kg) {
    return new PersonalBest(workoutId, exerciseId(exercise), new BigDecimal(kg).setScale(3));
  }

  private long exerciseId(String name) {
    return dsl.select(EXERCISES.ID)
        .from(EXERCISES)
        .where(EXERCISES.NAME.eq(name))
        .and(EXERCISES.OWNER_ID.isNull())
        .fetchSingle()
        .value1();
  }

  private static StoredSet wr(String kg, int reps) {
    return new StoredSet(new BigDecimal(kg).setScale(3), reps, null, null);
  }

  private long user(String username) {
    return userRepository
        .insert(new NewUser(username, "表示名", username + "@example.com", "hash", AT.minusYears(1)))
        .getId();
  }
}
