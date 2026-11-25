package com.okimoto.fitlog.comment;

import static com.okimoto.fitlog.jooq.Tables.NOTIFICATIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.okimoto.fitlog.support.SqlCounter;
import com.okimoto.fitlog.support.TestAccounts;
import com.okimoto.fitlog.support.TestAccounts.Account;
import com.okimoto.fitlog.support.TestcontainersConfiguration;
import com.okimoto.fitlog.support.WorkoutFixture;
import com.okimoto.fitlog.workout.repository.WorkoutRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * コメント・返信（A-60〜A-62・A-65）と、その通知・数（docs/api.md「コメント・ナイストレーニング」、docs/test-plan.md 4.1・4.7・5.1・5.9）
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommentIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DSLContext dsl;
  @Autowired private WorkoutRepository workoutRepository;
  @Autowired private SqlCounter sqlCounter;

  private TestAccounts accounts;
  private WorkoutFixture workouts;

  @BeforeEach
  void setUp() {
    accounts = new TestAccounts(mockMvc, objectMapper, dsl);
    workouts = new WorkoutFixture(dsl, workoutRepository);
  }

  @Nested
  class 投稿_A61 {

    @Test
    void コメントを作り_コメント数を返し_投稿者に抜粋つきのcommentの通知が届く() throws Exception {
      Account me = accounts.signup();
      Account author = accounts.signup();
      long workout = workouts.of(author.id()).save();

      long id =
          json(comment(me, workout, "  フォームきれいでした！\r\n ", null)
                  .andExpect(status().isCreated())
                  .andExpect(jsonPath("$.commentCount").value(1))
                  .andExpect(jsonPath("$.comment.author.username").value(me.username()))
                  .andExpect(jsonPath("$.comment.body").value("フォームきれいでした！"))
                  .andExpect(jsonPath("$.comment.deletable").value(true))
                  .andExpect(jsonPath("$.comment.replies", hasSize(0)))
                  .andExpect(jsonPath("$.comment.replyCount").value(0))
                  .andExpect(jsonPath("$.comment.repliesNextCursor").value(nullValue())))
              .get("comment")
              .get("id")
              .asLong();

      awaitNotifications(author, Map.of("comment", 1));
      mockMvc
          .perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, author.bearer()))
          .andExpect(jsonPath("$.items[0].type").value("comment"))
          .andExpect(jsonPath("$.items[0].comment.id").value(id))
          .andExpect(jsonPath("$.items[0].comment.bodyExcerpt").value("フォームきれいでした！"));
    }

    @Test
    void 本文が空や141文字なら400_140文字は作れる() throws Exception {
      Account me = accounts.signup();
      long workout = workouts.of(me.id()).save();

      comment(me, workout, " \r\n ", null)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.errors[0].field").value("body"));
      comment(me, workout, "あ".repeat(141), null).andExpect(status().isBadRequest());
      comment(me, workout, "あ".repeat(140), null).andExpect(status().isCreated());
    }

    @Test
    void ほかの人のコメントへの返信は_返信先にreply_投稿者にcomment() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      Account author = accounts.signup();
      long workout = workouts.of(author.id()).save();
      long parent = commentId(sato, workout, "コメント", null);
      awaitNotifications(author, Map.of("comment", 1));

      comment(me, workout, "返信です", parent)
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.commentCount").value(2))
          .andExpect(jsonPath("$.comment.replyToUser.username").value(sato.username()))
          .andExpect(jsonPath("$.comment.replies").doesNotExist());

      awaitNotifications(sato, Map.of("reply", 1));
      awaitNotifications(author, Map.of("comment", 2));
    }

    @Test
    void 投稿者のコメントへの返信は_投稿者にreplyを1件だけ() throws Exception {
      Account me = accounts.signup();
      Account author = accounts.signup();
      long workout = workouts.of(author.id()).save();
      long parent = commentId(author, workout, "見てくれてありがとう", null);

      comment(me, workout, "どういたしまして", parent).andExpect(status().isCreated());

      awaitNotifications(author, Map.of("reply", 1));
      await()
          .during(Duration.ofSeconds(1))
          .atMost(Duration.ofSeconds(2))
          .untilAsserted(() -> assertThat(notifications(author)).isEqualTo(Map.of("reply", 1)));
    }

    @Test
    void 返信への返信は_同じ返信元につき_返信先はその返信を書いた人() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      Account suzuki = accounts.signup();
      long workout = workouts.of(sato.id()).save();
      long parent = commentId(sato, workout, "コメント", null);
      long reply = commentId(suzuki, workout, "返信", parent);

      comment(me, workout, "返信への返信", reply)
          .andExpect(jsonPath("$.comment.replyToUser.username").value(suzuki.username()));

      listComments(me, workout, null)
          .andExpect(jsonPath("$.items", hasSize(1)))
          .andExpect(jsonPath("$.items[0].replyCount").value(2))
          .andExpect(jsonPath("$.items[0].replies[1].body").value("返信への返信"));
    }

    @Test
    void 自分の記録へのコメントは_通知を作らない() throws Exception {
      Account me = accounts.signup();
      long workout = workouts.of(me.id()).save();

      comment(me, workout, "メモ", null).andExpect(status().isCreated());

      await()
          .during(Duration.ofSeconds(1))
          .atMost(Duration.ofSeconds(2))
          .untilAsserted(() -> assertThat(notifications(me)).isEmpty());
    }

    @Test
    void 返信元がほかの投稿のコメント_ないコメントなら404() throws Exception {
      Account me = accounts.signup();
      long workout = workouts.of(me.id()).save();
      long other = workouts.of(me.id()).save();
      long othersComment = commentId(me, other, "ほかの投稿", null);

      comment(me, workout, "返信", othersComment)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
      comment(me, workout, "返信", Long.MAX_VALUE).andExpect(status().isNotFound());
    }
  }

  @Nested
  class 一覧_A60 {

    @Test
    void 返信は3件まで入れ_4件目からは続きのカーソル() throws Exception {
      Account me = accounts.signup();
      long workout = workouts.of(me.id()).save();
      long three = commentId(me, workout, "返信3件", null);
      long four = commentId(me, workout, "返信4件", null);
      IntStream.range(0, 3).forEach(i -> commentIdUnchecked(me, workout, "r" + i, three));
      IntStream.range(0, 4).forEach(i -> commentIdUnchecked(me, workout, "r" + i, four));

      listComments(me, workout, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items", hasSize(2)))
          .andExpect(jsonPath("$.items[0].replies", hasSize(3)))
          .andExpect(jsonPath("$.items[0].replyCount").value(3))
          .andExpect(jsonPath("$.items[0].repliesNextCursor").value(nullValue()))
          .andExpect(jsonPath("$.items[1].replies", hasSize(3)))
          .andExpect(jsonPath("$.items[1].replyCount").value(4))
          .andExpect(jsonPath("$.items[1].repliesNextCursor").isString())
          .andExpect(jsonPath("$.items[1].replies[0].replies").doesNotExist());
    }

    @Test
    void 古い順に20件ずつ_続きを読み込める() throws Exception {
      Account me = accounts.signup();
      long workout = workouts.of(me.id()).save();
      List<Long> ids =
          IntStream.range(0, 21)
              .mapToObj(i -> commentIdUnchecked(me, workout, "c" + i, null))
              .toList();

      JsonNode first =
          json(listComments(me, workout, null).andExpect(jsonPath("$.items", hasSize(20))));
      assertThat(first.get("hasNext").asBoolean()).isTrue();
      assertThat(first.get("items").get(0).get("id").asLong()).isEqualTo(ids.get(0));

      listComments(me, workout, first.get("nextCursor").asString())
          .andExpect(jsonPath("$.items", hasSize(1)))
          .andExpect(jsonPath("$.items[0].id").value(ids.get(20)))
          .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void ちょうど20件なら_続きはない() throws Exception {
      Account me = accounts.signup();
      long workout = workouts.of(me.id()).save();
      IntStream.range(0, 20).forEach(i -> commentIdUnchecked(me, workout, "c" + i, null));

      listComments(me, workout, null)
          .andExpect(jsonPath("$.items", hasSize(20)))
          .andExpect(jsonPath("$.hasNext").value(false))
          .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void カーソルが壊れていれば400() throws Exception {
      Account me = accounts.signup();
      long workout = workouts.of(me.id()).save();

      listComments(me, workout, "壊れた")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("cursor"));
    }
  }

  @Nested
  class 返信の続き_A65 {

    @Test
    void 一覧のカーソルで4件目から_カーソルなしなら1件目から読む() throws Exception {
      Account me = accounts.signup();
      long workout = workouts.of(me.id()).save();
      long parent = commentId(me, workout, "コメント", null);
      List<Long> replies =
          IntStream.range(0, 5)
              .mapToObj(i -> commentIdUnchecked(me, workout, "r" + i, parent))
              .toList();
      String cursor =
          json(listComments(me, workout, null))
              .get("items")
              .get(0)
              .get("repliesNextCursor")
              .asString();

      listReplies(me, parent, cursor)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items", hasSize(2)))
          .andExpect(jsonPath("$.items[0].id").value(replies.get(3)))
          .andExpect(jsonPath("$.hasNext").value(false));
      listReplies(me, parent, null)
          .andExpect(jsonPath("$.items", hasSize(5)))
          .andExpect(jsonPath("$.items[0].id").value(replies.get(0)));
    }

    @Test
    void 返信は20件ずつ() throws Exception {
      Account me = accounts.signup();
      long workout = workouts.of(me.id()).save();
      long parent = commentId(me, workout, "コメント", null);
      IntStream.range(0, 21).forEach(i -> commentIdUnchecked(me, workout, "r" + i, parent));

      JsonNode first =
          json(listReplies(me, parent, null).andExpect(jsonPath("$.items", hasSize(20))));
      listReplies(me, parent, first.get("nextCursor").asString())
          .andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void 返信のIDやないコメントなら404() throws Exception {
      Account me = accounts.signup();
      long workout = workouts.of(me.id()).save();
      long parent = commentId(me, workout, "コメント", null);
      long reply = commentId(me, workout, "返信", parent);

      listReplies(me, reply, null)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
      listReplies(me, Long.MAX_VALUE, null).andExpect(status().isNotFound());
    }
  }

  @Nested
  class 削除_A62 {

    @Test
    void 書いた本人は消せて_削除後のコメント数を返す_返信元なら返信と通知も消える() throws Exception {
      Account me = accounts.signup();
      Account author = accounts.signup();
      long workout = workouts.of(author.id()).save();
      long parent = commentId(me, workout, "コメント", null);
      commentId(author, workout, "返信", parent);
      commentId(author, workout, "残るコメント", null);
      awaitNotifications(author, Map.of("comment", 1));

      deleteComment(me, parent)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.commentCount").value(1));

      assertThat(notifications(author)).isEmpty();
      await()
          .atMost(Duration.ofSeconds(5))
          .untilAsserted(() -> assertThat(notifications(me)).isEmpty());
    }

    @Test
    void 投稿者はほかの人のコメントも消せる() throws Exception {
      Account me = accounts.signup();
      Account author = accounts.signup();
      long workout = workouts.of(author.id()).save();
      long comment = commentId(me, workout, "コメント", null);

      deleteComment(author, comment)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.commentCount").value(0));
    }

    @Test
    void 書いた本人でも投稿者でもなければ403() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      Account author = accounts.signup();
      long workout = workouts.of(author.id()).save();
      long comment = commentId(sato, workout, "コメント", null);

      deleteComment(me, comment)
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void ないコメントは404() throws Exception {
      Account me = accounts.signup();

      deleteComment(me, Long.MAX_VALUE)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
    }
  }

  /** docs/test-plan.md 4.1 ②。一番間違えやすい「鍵アカウントの公開の投稿を、リクエスト中の人が見る」で確かめる */
  @Nested
  class 見てはいけない投稿 {

    @Test
    void 一覧と投稿は404_返信の続きと削除はCOMMENT_NOT_FOUND() throws Exception {
      Account me = accounts.signup();
      Account locked = accounts.signup();
      long workout = workouts.of(locked.id()).save();
      long comment = commentId(locked, workout, "コメント", null);
      accounts.makePrivate(locked);
      follow(me, locked);

      listComments(me, workout, null)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("WORKOUT_NOT_FOUND"));
      comment(me, workout, "コメント", null)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("WORKOUT_NOT_FOUND"));
      listReplies(me, comment, null)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
      deleteComment(me, comment)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
    }
  }

  @Test
  void 記録の詳細と一覧の1件のコメント数は_返信を含む() throws Exception {
    Account me = accounts.signup();
    long workout = workouts.of(me.id()).save();
    long parent = commentId(me, workout, "コメント", null);
    commentId(me, workout, "返信", parent);

    mockMvc
        .perform(get("/api/workouts/" + workout).header(HttpHeaders.AUTHORIZATION, me.bearer()))
        .andExpect(jsonPath("$.commentCount").value(2));
    mockMvc
        .perform(
            get("/api/users/" + me.username() + "/workouts")
                .header(HttpHeaders.AUTHORIZATION, me.bearer()))
        .andExpect(jsonPath("$.items[0].commentCount").value(2));
  }

  /** 1件のときと1ページ分（20件）のときで、SQL の数が同じ（docs/test-plan.md 4.7） */
  @Test
  void N1がない_コメント一覧と返信の続き() throws Exception {
    Account me = accounts.signup();
    long workout = workouts.of(me.id()).save();
    long first = commentId(me, workout, "コメント", null);
    IntStream.range(0, 4).forEach(i -> commentIdUnchecked(me, workout, "r", first));
    int listOne =
        sqlCounter.count(
            () -> listComments(me, workout, null).andExpect(jsonPath("$.items", hasSize(1))));
    int repliesFour =
        sqlCounter.count(
            () -> listReplies(me, first, null).andExpect(jsonPath("$.items", hasSize(4))));

    IntStream.range(0, 19)
        .forEach(i -> {
          long parent = commentIdUnchecked(me, workout, "c", null);
          IntStream.range(0, 4).forEach(j -> commentIdUnchecked(me, workout, "r", parent));
        });
    IntStream.range(0, 16).forEach(i -> commentIdUnchecked(me, workout, "r", first));

    assertThat(listOne).isPositive();
    assertThat(
            sqlCounter.count(
                () -> listComments(me, workout, null).andExpect(jsonPath("$.items", hasSize(20)))))
        .isEqualTo(listOne);
    assertThat(
            sqlCounter.count(
                () -> listReplies(me, first, null).andExpect(jsonPath("$.items", hasSize(20)))))
        .isEqualTo(repliesFour);
  }

  private ResultActions comment(Account account, long workoutId, String body, Long replyTo)
      throws Exception {
    var payload = new java.util.HashMap<String, Object>();
    payload.put("body", body);
    if (replyTo != null) {
      payload.put("replyToCommentId", replyTo);
    }
    return mockMvc.perform(
        post("/api/workouts/" + workoutId + "/comments")
            .header(HttpHeaders.AUTHORIZATION, account.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(payload)));
  }

  private long commentId(Account account, long workoutId, String body, Long replyTo)
      throws Exception {
    return json(comment(account, workoutId, body, replyTo).andExpect(status().isCreated()))
        .get("comment")
        .get("id")
        .asLong();
  }

  /** ラムダの中で使うため、検査例外を投げない形 */
  private long commentIdUnchecked(Account account, long workoutId, String body, Long replyTo) {
    try {
      return commentId(account, workoutId, body, replyTo);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private ResultActions listComments(Account account, long workoutId, String cursor)
      throws Exception {
    var request =
        get("/api/workouts/" + workoutId + "/comments")
            .header(HttpHeaders.AUTHORIZATION, account.bearer());
    if (cursor != null) {
      request.param("cursor", cursor);
    }
    return mockMvc.perform(request);
  }

  private ResultActions listReplies(Account account, long commentId, String cursor)
      throws Exception {
    var request =
        get("/api/comments/" + commentId + "/replies")
            .header(HttpHeaders.AUTHORIZATION, account.bearer());
    if (cursor != null) {
      request.param("cursor", cursor);
    }
    return mockMvc.perform(request);
  }

  private ResultActions deleteComment(Account account, long commentId) throws Exception {
    return mockMvc.perform(
        delete("/api/comments/" + commentId).header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }

  private void follow(Account me, Account target) throws Exception {
    mockMvc
        .perform(
            post("/api/users/" + target.username() + "/follow")
                .header(HttpHeaders.AUTHORIZATION, me.bearer()))
        .andExpect(status().isOk());
  }

  private JsonNode json(ResultActions result) throws Exception {
    return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
  }

  /** コメント・返信の通知の、種類ごとの数（フォローなどほかの通知は除く） */
  private Map<String, Integer> notifications(Account recipient) {
    return dsl.select(NOTIFICATIONS.TYPE, org.jooq.impl.DSL.count())
        .from(NOTIFICATIONS)
        .where(NOTIFICATIONS.RECIPIENT_ID.eq(recipient.id()))
        .and(NOTIFICATIONS.TYPE.in("comment", "reply"))
        .groupBy(NOTIFICATIONS.TYPE)
        .fetchMap(Record2::value1, Record2::value2);
  }

  /** 通知は保存の確定後に別のスレッドで作るので、できるのを待つ */
  private void awaitNotifications(Account recipient, Map<String, Integer> counts) {
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(notifications(recipient)).isEqualTo(counts));
  }
}
