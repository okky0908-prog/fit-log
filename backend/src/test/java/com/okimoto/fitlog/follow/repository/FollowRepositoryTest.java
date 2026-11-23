package com.okimoto.fitlog.follow.repository;

import static com.okimoto.fitlog.jooq.Tables.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.okimoto.fitlog.common.calc.Cursor;
import com.okimoto.fitlog.support.TestcontainersConfiguration;
import com.okimoto.fitlog.user.repository.NewUser;
import com.okimoto.fitlog.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/** フォローの読み書き（docs/database.md「follows」、docs/test-plan.md 5.8）。本物の DB で確かめる（テストごとにロールバックする） */
@JooqTest
@Import({TestcontainersConfiguration.class, FollowRepository.class, UserRepository.class})
@ActiveProfiles("test")
class FollowRepositoryTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-23T10:00:00+09:00");

  @Autowired private FollowRepository followRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private DSLContext dsl;

  private long me;
  private long sato;
  private long suzuki;
  private long tanaka;

  @BeforeEach
  void setUp() {
    me = user("me_user");
    sato = user("sato");
    suzuki = user("suzuki");
    tanaka = user("tanaka");
  }

  @Test
  void フォローを作り_状態を取り出せる_もう一度作っても増えない() {
    assertThat(followRepository.insertIfAbsent(me, sato, "accepted", NOW)).isTrue();
    assertThat(followRepository.insertIfAbsent(me, sato, "pending", NOW)).isFalse();

    assertThat(followRepository.findStatus(me, sato)).contains("accepted");
    assertThat(followRepository.findStatus(sato, me)).as("向きが逆").isEmpty();
  }

  @Test
  void 通知を作る前の確かめでは_行をロックして状態を読む() {
    followRepository.insertIfAbsent(me, sato, "pending", NOW);

    assertThat(followRepository.lockStatus(me, sato)).contains("pending");
    assertThat(followRepository.lockStatus(sato, me)).as("向きが逆").isEmpty();
  }

  @Test
  void 解除すると_消した行の状態を返し_なければ空() {
    followRepository.insertIfAbsent(me, sato, "pending", NOW);

    assertThat(followRepository.delete(me, sato)).contains("pending");
    assertThat(followRepository.delete(me, sato)).isEmpty();
    assertThat(followRepository.findStatus(me, sato)).isEmpty();
  }

  @Test
  void 自分自身はDBでもフォローできない() {
    assertThatThrownBy(() -> followRepository.insertIfAbsent(me, me, "accepted", NOW))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void フォロー中とフォロワーの数は承認済みだけを数え_リクエストの数は承認待ちだけを数える() {
    followRepository.insertIfAbsent(me, sato, "accepted", NOW);
    followRepository.insertIfAbsent(me, suzuki, "pending", NOW);
    followRepository.insertIfAbsent(sato, me, "accepted", NOW);
    followRepository.insertIfAbsent(suzuki, me, "pending", NOW);
    followRepository.insertIfAbsent(tanaka, me, "pending", NOW);

    assertThat(followRepository.countFollowing(me)).isEqualTo(1);
    assertThat(followRepository.countFollowers(me)).isEqualTo(1);
    assertThat(followRepository.countPendingRequests(me)).isEqualTo(2);
    assertThat(followRepository.countPendingRequests(sato)).isZero();
  }

  @Test
  void フォロワー一覧は_承認済みだけを承認された日時の新しい順に_自分から見た状態を付けて返す() {
    followRepository.insertIfAbsent(sato, tanaka, "accepted", NOW.minusHours(2));
    followRepository.insertIfAbsent(suzuki, tanaka, "accepted", NOW.minusHours(1));
    followRepository.insertIfAbsent(me, tanaka, "accepted", NOW);
    followRepository.insertIfAbsent(user("pending_one"), tanaka, "pending", NOW);
    // 見ている自分から、それぞれへのフォローの状態
    followRepository.insertIfAbsent(me, sato, "pending", NOW);

    List<FollowUserRow> page = followRepository.findFollowers(tanaka, me, null, 20);

    assertThat(page)
        .extracting(FollowUserRow::username)
        .containsExactly("me_user", "suzuki", "sato");
    assertThat(page).extracting(FollowUserRow::myStatus).containsExactly(null, null, "pending");
    assertThat(page).extracting(FollowUserRow::me).containsExactly(true, false, false);
    assertThat(page.get(1).displayName()).isEqualTo("表示名");
    assertThat(page.get(1).bio()).isEqualTo("suzuki の自己紹介");
  }

  @Test
  void フォロー中一覧は_その人がフォローしている人を返し_続きはカーソルで取る() {
    followRepository.insertIfAbsent(tanaka, sato, "accepted", NOW.minusHours(2));
    followRepository.insertIfAbsent(tanaka, suzuki, "accepted", NOW.minusHours(1));
    followRepository.insertIfAbsent(tanaka, me, "pending", NOW);

    List<FollowUserRow> first = followRepository.findFollowing(tanaka, me, null, 1);
    FollowUserRow last = first.getLast();
    List<FollowUserRow> second =
        followRepository.findFollowing(
            tanaka, me, new Cursor.Position(last.acceptedAt(), last.id()), 1);

    assertThat(first).extracting(FollowUserRow::username).containsExactly("suzuki");
    assertThat(second).extracting(FollowUserRow::username).containsExactly("sato");
  }

  @Test
  void ユーザー名は大文字小文字を区別せずに探す() {
    assertThat(userRepository.findByUsernameIgnoreCase("SATO"))
        .get()
        .extracting(u -> u.getId())
        .isEqualTo(sato);
    assertThat(userRepository.findByUsernameIgnoreCase("nobody")).isEmpty();
  }

  private long user(String username) {
    NewUser user = new NewUser(username, "表示名", username + "@example.com", "hash", NOW);
    long id = userRepository.insert(user).getId();
    dsl.update(USERS).set(USERS.BIO, username + " の自己紹介").where(USERS.ID.eq(id)).execute();
    return id;
  }
}
