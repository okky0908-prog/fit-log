package com.okimoto.fitlog.comment.repository;

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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/** コメント・返信の読み書き。本物の DB で確かめる（テストごとにロールバックする） */
@JooqTest
@Import({
  TestcontainersConfiguration.class,
  CommentRepository.class,
  WorkoutRepository.class,
  UserRepository.class
})
@ActiveProfiles("test")
class CommentRepositoryTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-25T10:00:00+09:00");

  @Autowired private CommentRepository repository;
  @Autowired private WorkoutRepository workoutRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private DSLContext dsl;

  private WorkoutFixture workouts;

  /** 投稿者 */
  private long author;

  /** 見ている人 */
  private long me;

  private long sato;
  private long workout;

  @BeforeEach
  void setUp() {
    workouts = new WorkoutFixture(dsl, workoutRepository);
    author = user("cm_author");
    me = user("cm_me");
    sato = user("cm_sato");
    workout = workouts.of(author).save();
  }

  @Nested
  class コメント一覧 {

    @Test
    void 返信でないコメントを古い順に_同じ日時ならIDの小さい順に_件数まで返す() {
      long second = comment(sato, "2件目", NOW.plusMinutes(1));
      long first = comment(me, "1件目", NOW);
      long third = comment(sato, "3件目", NOW.plusMinutes(1));
      reply(second, me, sato, "返信", NOW.plusMinutes(2));
      comment(workouts.of(author).save(), sato, "ほかの投稿", NOW);

      List<CommentRow> page = repository.findPage(workout, me, null, 3);

      assertThat(page).extracting(CommentRow::id).containsExactly(first, second, third);
      CommentRow row = page.getFirst();
      assertThat(row.body()).isEqualTo("1件目");
      assertThat(row.createdAt()).isAtSameInstantAs(NOW);
      assertThat(row.author()).isEqualTo(new CommentUser("cm_me", "表示名", false, null));
    }

    @Test
    void カーソルより後から続きを返す() {
      long first = comment(sato, "1件目", NOW);
      long second = comment(sato, "2件目", NOW);
      long third = comment(sato, "3件目", NOW.plusMinutes(1));

      assertThat(repository.findPage(workout, me, new Cursor.Position(NOW, first), 20))
          .extracting(CommentRow::id)
          .containsExactly(second, third);
    }

    @Test
    void 返信は古い順に3件まで入れ_返信の総数も返す() {
      long parent = comment(sato, "コメント", NOW);
      long r1 = reply(parent, me, sato, "1", NOW.plusMinutes(1));
      long r2 = reply(parent, author, me, "2", NOW.plusMinutes(2));
      long r3 = reply(parent, sato, author, "3", NOW.plusMinutes(3));
      reply(parent, me, sato, "4", NOW.plusMinutes(4));

      CommentRow row = repository.findPage(workout, me, null, 20).getFirst();

      assertThat(row.replies()).extracting(ReplyRow::id).containsExactly(r1, r2, r3);
      assertThat(row.replyCount()).isEqualTo(4);
      ReplyRow first = row.replies().getFirst();
      assertThat(first.body()).isEqualTo("1");
      assertThat(first.author().username()).isEqualTo("cm_me");
      assertThat(first.replyToUser().username()).isEqualTo("cm_sato");
    }

    @Test
    void 返信がなければ空で0件() {
      comment(sato, "コメント", NOW);

      CommentRow row = repository.findPage(workout, me, null, 20).getFirst();

      assertThat(row.replies()).isEmpty();
      assertThat(row.replyCount()).isZero();
    }

    @Test
    void 消せるのは_書いた本人と投稿者() {
      long mine = comment(me, "自分のコメント", NOW);
      long others = comment(sato, "ほかの人のコメント", NOW.plusMinutes(1));
      reply(others, me, sato, "自分の返信", NOW.plusMinutes(2));
      reply(others, sato, me, "ほかの人の返信", NOW.plusMinutes(3));

      List<CommentRow> mePage = repository.findPage(workout, me, null, 20);
      assertThat(mePage).extracting(CommentRow::deletable).containsExactly(true, false);
      assertThat(mePage.get(1).replies())
          .extracting(ReplyRow::deletable)
          .containsExactly(true, false);

      // 投稿者は、ほかの人のコメント・返信も消せる
      List<CommentRow> authorPage = repository.findPage(workout, author, null, 20);
      assertThat(authorPage).extracting(CommentRow::deletable).containsExactly(true, true);
      assertThat(authorPage.get(1).replies())
          .extracting(ReplyRow::deletable)
          .containsExactly(true, true);
      assertThat(mine).isPositive();
    }

    @Test
    void 返信先の人が退会していれば_返信は残り_返信先はnull() {
      long suzuki = user("cm_suzuki");
      long parent = comment(suzuki, "コメント", NOW);
      long reply = reply(parent, sato, suzuki, "返信", NOW.plusMinutes(1));
      // 退会すると、書いたコメントは消え（返信元を消すので返信も消える）、ほかの返信は返信先だけ NULL になる
      long other = comment(sato, "ほかのコメント", NOW.plusMinutes(2));
      long replyToSuzuki = reply(other, me, suzuki, "@suzuki さんへ", NOW.plusMinutes(3));

      dsl.deleteFrom(USERS).where(USERS.ID.eq(suzuki)).execute();

      List<CommentRow> page = repository.findPage(workout, me, null, 20);
      assertThat(page).extracting(CommentRow::id).containsExactly(other);
      assertThat(page.getFirst().replies()).extracting(ReplyRow::id).containsExactly(replyToSuzuki);
      assertThat(page.getFirst().replies().getFirst().replyToUser()).isNull();
      assertThat(reply).isPositive();
    }
  }

  @Nested
  class 返信の続き {

    @Test
    void その返信元の返信を古い順に_カーソルより後から件数まで返す() {
      long parent = comment(sato, "コメント", NOW);
      long r1 = reply(parent, me, sato, "1", NOW.plusMinutes(1));
      long r2 = reply(parent, me, sato, "2", NOW.plusMinutes(2));
      long r3 = reply(parent, me, sato, "3", NOW.plusMinutes(2));
      long r4 = reply(parent, me, sato, "4", NOW.plusMinutes(3));
      reply(comment(sato, "ほか", NOW), me, sato, "ほかの返信", NOW.plusMinutes(1));

      assertThat(repository.findReplies(parent, me, null, 2))
          .extracting(ReplyRow::id)
          .containsExactly(r1, r2);
      assertThat(
              repository.findReplies(parent, me, new Cursor.Position(NOW.plusMinutes(2), r2), 20))
          .extracting(ReplyRow::id)
          .containsExactly(r3, r4);
    }
  }

  @Nested
  class 対象のコメント {

    @Test
    void 見てよい投稿のコメントなら_投稿と投稿者と返信元と書いた人を返す() {
      long parent = comment(sato, "コメント", NOW);
      long reply = reply(parent, me, sato, "返信", NOW);

      assertThat(repository.findVisibleTarget(parent, me))
          .contains(new CommentTarget(parent, workout, author, null, sato));
      assertThat(repository.findVisibleTarget(reply, me))
          .contains(new CommentTarget(reply, workout, author, parent, me));
    }

    @Test
    void 見てはいけない投稿のコメントと_ないコメントは空() {
      long privateWorkout = workouts.of(author).visibility("private").save();
      long hidden = comment(privateWorkout, author, "自分のみの投稿へのコメント", NOW);

      assertThat(repository.findVisibleTarget(hidden, me)).isEmpty();
      assertThat(repository.findVisibleTarget(hidden, author)).isPresent();
      assertThat(repository.findVisibleTarget(Long.MAX_VALUE, me)).isEmpty();
    }
  }

  @Test
  void 書いたコメント1件を_返信の形で読む() {
    long parent = comment(sato, "コメント", NOW);
    long reply = reply(parent, me, sato, "返信", NOW);

    ReplyRow row = repository.findOne(reply, me).orElseThrow();

    assertThat(row.body()).isEqualTo("返信");
    assertThat(row.author().username()).isEqualTo("cm_me");
    assertThat(row.replyToUser().username()).isEqualTo("cm_sato");
    assertThat(row.deletable()).isTrue();
    assertThat(repository.findOne(parent, me).orElseThrow().replyToUser()).isNull();
  }

  @Test
  void コメント数は返信を含めて_その投稿の分だけ数える() {
    long parent = comment(sato, "コメント", NOW);
    reply(parent, me, sato, "返信", NOW);
    comment(me, "コメント2", NOW);
    comment(workouts.of(author).save(), sato, "ほかの投稿", NOW);

    assertThat(repository.count(workout)).isEqualTo(3);
  }

  @Test
  void 返信元を消すと_返信も消える() {
    long parent = comment(sato, "コメント", NOW);
    reply(parent, me, sato, "返信", NOW);
    long kept = comment(me, "残るコメント", NOW);

    repository.delete(parent);

    assertThat(repository.count(workout)).isEqualTo(1);
    assertThat(repository.lockExists(kept)).isTrue();
    assertThat(repository.lockExists(parent)).isFalse();
  }

  @Test
  void 返信でないコメントに返信先は_DBでも持たせられない() {
    assertThatThrownBy(() -> repository.insert(new NewComment(workout, me, null, sato, "本文", NOW)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 空の本文は_DBでも入れられない() {
    assertThatThrownBy(() -> repository.insert(new NewComment(workout, me, null, null, "", NOW)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private long comment(long userId, String body, OffsetDateTime at) {
    return comment(workout, userId, body, at);
  }

  private long comment(long workoutId, long userId, String body, OffsetDateTime at) {
    return repository.insert(new NewComment(workoutId, userId, null, null, body, at));
  }

  private long reply(
      long parentId, long userId, long replyToUserId, String body, OffsetDateTime at) {
    return repository.insert(new NewComment(workout, userId, parentId, replyToUserId, body, at));
  }

  private long user(String username) {
    return userRepository
        .insert(new NewUser(username, "表示名", username + "@example.com", "hash", NOW))
        .getId();
  }
}
