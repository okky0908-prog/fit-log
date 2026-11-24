package com.okimoto.fitlog.like;

import static com.okimoto.fitlog.jooq.Tables.LIKES;
import static com.okimoto.fitlog.jooq.Tables.NOTIFICATIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.okimoto.fitlog.support.TestAccounts;
import com.okimoto.fitlog.support.TestAccounts.Account;
import com.okimoto.fitlog.support.TestcontainersConfiguration;
import com.okimoto.fitlog.support.WorkoutFixture;
import com.okimoto.fitlog.workout.repository.WorkoutRepository;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/** ナイストレーニング（A-63・A-64）と、その通知・数（docs/api.md「コメント・ナイストレーニング」、docs/test-plan.md 4.1・4.5・4.6・5.9） */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LikeIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DSLContext dsl;
  @Autowired private WorkoutRepository workoutRepository;

  private TestAccounts accounts;
  private WorkoutFixture workouts;

  @BeforeEach
  void setUp() {
    accounts = new TestAccounts(mockMvc, objectMapper, dsl);
    workouts = new WorkoutFixture(dsl, workoutRepository);
  }

  @Nested
  class 付ける_A63 {

    @Test
    void 数と付けたかを返し_投稿者にlikeの通知が届く() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      long workout = workouts.of(sato.id()).save();

      like(me, workout)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.likeCount").value(1))
          .andExpect(jsonPath("$.likedByMe").value(true));

      awaitNotifications(sato, List.of("like"));
    }

    @Test
    void もう一度付けても_数も通知も増えない() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      long workout = workouts.of(sato.id()).save();
      like(me, workout);
      awaitNotifications(sato, List.of("like"));

      like(me, workout)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.likeCount").value(1))
          .andExpect(jsonPath("$.likedByMe").value(true));

      // 「起きないこと」は、決めた時間（1秒）待って確かめる（docs/test-plan.md 4.5）
      await()
          .during(Duration.ofSeconds(1))
          .atMost(Duration.ofSeconds(2))
          .untilAsserted(() -> assertThat(notificationTypes(sato)).containsExactly("like"));
    }

    @Test
    void ほかの人の分も数える() throws Exception {
      Account me = accounts.signup();
      Account suzuki = accounts.signup();
      Account sato = accounts.signup();
      long workout = workouts.of(sato.id()).save();
      like(suzuki, workout);

      like(me, workout).andExpect(jsonPath("$.likeCount").value(2));
    }

    @Test
    void 自分の記録にも付けられるが_通知は作らない() throws Exception {
      Account me = accounts.signup();
      long workout = workouts.of(me.id()).save();

      like(me, workout).andExpect(status().isOk()).andExpect(jsonPath("$.likeCount").value(1));

      await()
          .during(Duration.ofSeconds(1))
          .atMost(Duration.ofSeconds(2))
          .untilAsserted(() -> assertThat(notificationTypes(me)).isEmpty());
    }

    /** docs/test-plan.md 4.6。2つのスレッドから同時に呼ぶ */
    @Test
    void 同時に2回付けても_行も通知も1件() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      long workout = workouts.of(sato.id()).save();
      CountDownLatch start = new CountDownLatch(1);
      try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
        List<Future<Integer>> results =
            List.of(1, 2).stream()
                .map(i ->
                    executor.submit(() -> {
                      start.await();
                      return like(me, workout).andReturn().getResponse().getStatus();
                    }))
                .toList();
        start.countDown();

        assertThat(List.of(results.get(0).get(), results.get(1).get())).containsOnly(200);
      }

      assertThat(dsl.fetchCount(LIKES, LIKES.WORKOUT_ID.eq(workout))).isEqualTo(1);
      await()
          .during(Duration.ofSeconds(1))
          .atMost(Duration.ofSeconds(2))
          .untilAsserted(() -> assertThat(notificationTypes(sato)).containsExactly("like"));
    }
  }

  @Nested
  class 取り消す_A64 {

    @Test
    void 数と付けたかを返し_通知を消す() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      long workout = workouts.of(sato.id()).save();
      like(me, workout);
      awaitNotifications(sato, List.of("like"));

      unlike(me, workout)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.likeCount").value(0))
          .andExpect(jsonPath("$.likedByMe").value(false));

      assertThat(notificationTypes(sato)).isEmpty();
    }

    @Test
    void ほかの記録への通知とほかの人の通知は消さない() throws Exception {
      Account me = accounts.signup();
      Account suzuki = accounts.signup();
      Account sato = accounts.signup();
      long workout = workouts.of(sato.id()).save();
      long other = workouts.of(sato.id()).save();
      like(me, workout);
      like(me, other);
      like(suzuki, workout);
      awaitNotifications(sato, List.of("like", "like", "like"));

      unlike(me, workout).andExpect(jsonPath("$.likeCount").value(1));

      assertThat(notificationTypes(sato)).hasSize(2);
    }

    @Test
    void 付いていなくてもエラーにしない() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      long workout = workouts.of(sato.id()).save();

      unlike(me, workout)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.likeCount").value(0))
          .andExpect(jsonPath("$.likedByMe").value(false));
    }

    /** 通知は確定後に別のスレッドで作るので、付けてすぐ取り消したときに、取り消しのあとで通知ができないこと（docs/test-plan.md 4.5） */
    @Test
    void 付けてすぐ取り消すと_通知は残らない() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      long workout = workouts.of(sato.id()).save();

      like(me, workout);
      unlike(me, workout);

      await()
          .during(Duration.ofSeconds(1))
          .atMost(Duration.ofSeconds(2))
          .untilAsserted(() -> assertThat(notificationTypes(sato)).isEmpty());
    }
  }

  /** docs/test-plan.md 4.1 ②。一番間違えやすい「鍵アカウントの公開の投稿を、リクエスト中の人が見る」で確かめる */
  @Nested
  class 見てはいけない記録 {

    @Test
    void 付けるのも取り消すのも404() throws Exception {
      Account me = accounts.signup();
      Account locked = accounts.signup();
      accounts.makePrivate(locked);
      follow(me, locked);
      long workout = workouts.of(locked.id()).save();

      like(me, workout)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("WORKOUT_NOT_FOUND"));
      unlike(me, workout)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("WORKOUT_NOT_FOUND"));
      assertThat(dsl.fetchCount(LIKES, LIKES.WORKOUT_ID.eq(workout))).isZero();
    }

    @Test
    void ない記録も404() throws Exception {
      Account me = accounts.signup();

      like(me, Long.MAX_VALUE).andExpect(status().isNotFound());
    }
  }

  /** 記録の詳細（A-21）と一覧の1件（WorkoutCard）の数と「付けたか」は、見ている人から見た値 */
  @Nested
  class 詳細と一覧の数 {

    @Test
    void 付けた人には付けた_ほかの人には付けていないと出る() throws Exception {
      Account me = accounts.signup();
      Account suzuki = accounts.signup();
      Account sato = accounts.signup();
      long workout = workouts.of(sato.id()).save();
      like(me, workout);
      like(suzuki, workout);

      detail(me, workout)
          .andExpect(jsonPath("$.likeCount").value(2))
          .andExpect(jsonPath("$.likedByMe").value(true));
      detail(sato, workout)
          .andExpect(jsonPath("$.likeCount").value(2))
          .andExpect(jsonPath("$.likedByMe").value(false));
      userWorkouts(me, sato)
          .andExpect(jsonPath("$.items[0].likeCount").value(2))
          .andExpect(jsonPath("$.items[0].likedByMe").value(true));
      userWorkouts(sato, sato)
          .andExpect(jsonPath("$.items[0].likeCount").value(2))
          .andExpect(jsonPath("$.items[0].likedByMe").value(false));
    }
  }

  private ResultActions like(Account account, long workoutId) throws Exception {
    return mockMvc.perform(
        post("/api/workouts/" + workoutId + "/likes")
            .header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }

  private ResultActions unlike(Account account, long workoutId) throws Exception {
    return mockMvc.perform(
        delete("/api/workouts/" + workoutId + "/likes")
            .header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }

  private ResultActions detail(Account account, long workoutId) throws Exception {
    return mockMvc.perform(
        get("/api/workouts/" + workoutId).header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }

  private ResultActions userWorkouts(Account account, Account owner) throws Exception {
    return mockMvc.perform(
        get("/api/users/" + owner.username() + "/workouts")
            .header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }

  private void follow(Account me, Account target) throws Exception {
    mockMvc
        .perform(
            post("/api/users/" + target.username() + "/follow")
                .header(HttpHeaders.AUTHORIZATION, me.bearer()))
        .andExpect(status().isOk());
  }

  /** ナイストレーニングの通知の種類（フォローのリクエストなどほかの通知は除く） */
  private List<String> notificationTypes(Account recipient) {
    return dsl.select(NOTIFICATIONS.TYPE)
        .from(NOTIFICATIONS)
        .where(NOTIFICATIONS.RECIPIENT_ID.eq(recipient.id()))
        .and(NOTIFICATIONS.TYPE.eq("like"))
        .orderBy(NOTIFICATIONS.ID)
        .fetch(NOTIFICATIONS.TYPE);
  }

  /** 通知は保存の確定後に別のスレッドで作るので、できるのを待つ */
  private void awaitNotifications(Account recipient, List<String> types) {
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(notificationTypes(recipient)).isEqualTo(types));
  }
}
