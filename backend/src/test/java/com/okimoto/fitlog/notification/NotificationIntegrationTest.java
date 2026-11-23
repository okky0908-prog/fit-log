package com.okimoto.fitlog.notification;

import static com.okimoto.fitlog.jooq.Tables.NOTIFICATIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.okimoto.fitlog.notification.repository.NewNotification;
import com.okimoto.fitlog.notification.repository.NotificationRepository;
import com.okimoto.fitlog.notification.service.NotificationEvent;
import com.okimoto.fitlog.support.TestAccounts;
import com.okimoto.fitlog.support.TestAccounts.Account;
import com.okimoto.fitlog.support.TestcontainersConfiguration;
import com.okimoto.fitlog.support.WorkoutFixture;
import com.okimoto.fitlog.workout.repository.WorkoutRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.LongStream;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 通知（A-120〜A-122）と、保存の確定後に通知を作る部品（docs/api.md「通知」、docs/test-plan.md 4.1・4.5・5.9） */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationIntegrationTest {

  private static final OffsetDateTime BASE = OffsetDateTime.parse("2026-11-23T10:00:00+09:00");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DSLContext dsl;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private WorkoutRepository workoutRepository;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private TransactionTemplate transactions;

  private TestAccounts accounts;
  private WorkoutFixture workouts;

  @BeforeEach
  void setUp() {
    accounts = new TestAccounts(mockMvc, objectMapper, dsl);
    workouts = new WorkoutFixture(dsl, workoutRepository);
  }

  @Nested
  class 一覧_A120 {

    @Test
    void 新しい順に20件ずつ返し_続きはカーソルで取る() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      List<Long> ids =
          LongStream.range(0, 21).mapToObj(i -> follow(sato, me, BASE.plusMinutes(i))).toList();

      JsonNode first = json(list(me, null).andExpect(status().isOk()));
      assertThat(first.get("items").size()).isEqualTo(20);
      assertThat(first.get("items").get(0).get("id").asLong()).isEqualTo(ids.get(20));
      assertThat(first.get("hasNext").asBoolean()).isTrue();

      JsonNode second =
          json(list(me, first.get("nextCursor").asString()).andExpect(status().isOk()));
      assertThat(second.get("items").size()).isEqualTo(1);
      assertThat(second.get("items").get(0).get("id").asLong()).isEqualTo(ids.get(0));
      assertThat(second.get("hasNext").asBoolean()).isFalse();
      assertThat(second.get("nextCursor").isNull()).isTrue();
    }

    @Test
    void 操作した人と対象の投稿の抜き出しを返し_一覧を取っただけでは既読にしない() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      long workout = workouts.of(me.id()).body("あ".repeat(31)).save();
      like(sato, me, workout, BASE);

      list(me, null)
          .andExpect(jsonPath("$.items[0].type").value("like"))
          .andExpect(jsonPath("$.items[0].createdAt").value("2026-11-23T10:00:00+09:00"))
          .andExpect(jsonPath("$.items[0].read").value(false))
          .andExpect(jsonPath("$.items[0].actor.username").value(sato.username()))
          .andExpect(jsonPath("$.items[0].actor.displayName").value("表示名"))
          .andExpect(jsonPath("$.items[0].actor.iconUrl").isEmpty())
          .andExpect(jsonPath("$.items[0].actor.isPrivate").value(false))
          .andExpect(jsonPath("$.items[0].workout.id").value(workout))
          .andExpect(jsonPath("$.items[0].workout.bodyExcerpt").value("あ".repeat(30) + "…"))
          .andExpect(jsonPath("$.items[0].comment").isEmpty())
          .andExpect(jsonPath("$.items[0].goal").isEmpty());
      list(me, null).andExpect(jsonPath("$.items[0].read").value(false));
    }

    /** 応答のキーは許可リストと同じ（docs/test-plan.md 4.2）。キーを足したら、受け取る人に出してよいかを考えてからここに足す */
    @Test
    void 応答のキーは許可リストと同じ() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      like(sato, me, workouts.of(me.id()).save(), BASE);

      JsonNode body = json(list(me, null));

      TreeSet<String> keys = new TreeSet<>();
      body.properties().forEach(entry -> keys.add(entry.getKey()));
      body.get("items").get(0).properties().forEach(entry -> keys.add("items[]." + entry.getKey()));
      body.get("items")
          .get(0)
          .get("actor")
          .properties()
          .forEach(entry -> keys.add("items[].actor." + entry.getKey()));
      body.get("items")
          .get(0)
          .get("workout")
          .properties()
          .forEach(entry -> keys.add("items[].workout." + entry.getKey()));
      assertThat(keys)
          .containsExactlyInAnyOrder(
              "items",
              "nextCursor",
              "hasNext",
              "items[].id",
              "items[].type",
              "items[].createdAt",
              "items[].read",
              "items[].actor",
              "items[].workout",
              "items[].comment",
              "items[].goal",
              "items[].actor.username",
              "items[].actor.displayName",
              "items[].actor.iconUrl",
              "items[].actor.isPrivate",
              "items[].workout.id",
              "items[].workout.bodyExcerpt");
    }

    @Test
    void 見られなくなった投稿の通知は_一覧にも未読の数にも出ない() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      long hidden = workouts.of(sato.id()).visibility("private").save();
      like(sato, me, hidden, BASE);
      long shown = follow(sato, me, BASE);

      list(me, null)
          .andExpect(jsonPath("$.items.length()").value(1))
          .andExpect(jsonPath("$.items[0].id").value(shown));
      unread(me).andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void カーソルが壊れていれば400() throws Exception {
      list(accounts.signup(), "壊れた")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.errors[0].field").value("cursor"));
    }
  }

  @Nested
  class 未読の数_A121 {

    @Test
    void 未読の数を返し_100で数えるのをやめる_フォローのリクエストの数は0() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      unread(me)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.unreadCount").value(0))
          .andExpect(jsonPath("$.followRequestCount").value(0));

      for (int i = 0; i < 101; i++) {
        follow(sato, me, BASE.plusSeconds(i));
      }

      unread(me).andExpect(jsonPath("$.unreadCount").value(100));
    }
  }

  @Nested
  class 既読_A122 {

    @Test
    void 自分の未読の通知だけを既読にし_ほかの人の通知は変えない_何度呼んでも同じ() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      long mine = follow(sato, me, BASE);
      long others = follow(me, sato, BASE);

      read(me, List.of(mine, others)).andExpect(status().isNoContent());
      read(me, List.of(mine, others)).andExpect(status().isNoContent());

      list(me, null).andExpect(jsonPath("$.items[0].read").value(true));
      unread(me).andExpect(jsonPath("$.unreadCount").value(0));
      unread(sato).andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void 一度に50件まで_51件は400() throws Exception {
      Account me = accounts.signup();

      read(me, LongStream.rangeClosed(1, 50).boxed().toList()).andExpect(status().isNoContent());
      read(me, LongStream.rangeClosed(1, 51).boxed().toList())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("ids"))
          .andExpect(jsonPath("$.errors[0].message").value("既読にする通知は50件まで指定できます"));
    }

    @Test
    void 空の配列なら何もしない() throws Exception {
      Account me = accounts.signup();
      follow(accounts.signup(), me, BASE);

      read(me, List.of()).andExpect(status().isNoContent());

      unread(me).andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void IDの指定がなければ400() throws Exception {
      mockMvc
          .perform(
              post("/api/notifications/read")
                  .header(HttpHeaders.AUTHORIZATION, accounts.signup().bearer())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("ids"));
    }
  }

  @Test
  void ログインしていなければ_どれも401() throws Exception {
    mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/notifications/read")).andExpect(status().isUnauthorized());
  }

  @Nested
  class 通知を作る部品 {

    // 確かめる役（NotificationGuard）のない種類で、通知を作る部品そのものを確かめる
    @Test
    void 保存が確定したら_別のスレッドで通知ができる() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();

      transactions.executeWithoutResult(status ->
          events.publishEvent(new NotificationEvent("follow_accepted", me.id(), sato.id(), null)));

      await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(count(me)).isEqualTo(1));
    }

    @Test
    void 保存が取り消されたら_通知を作らない() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();

      transactions.executeWithoutResult(status -> {
        events.publishEvent(new NotificationEvent("follow_accepted", me.id(), sato.id(), null));
        status.setRollbackOnly();
      });

      // 「起きないこと」は、決めた時間（1秒）待って確かめる（docs/test-plan.md 4.5）
      await()
          .during(Duration.ofSeconds(1))
          .atMost(Duration.ofSeconds(2))
          .untilAsserted(() -> assertThat(count(me)).isZero());
    }
  }

  // ===== 補助 =====

  private long follow(Account actor, Account recipient, OffsetDateTime at) {
    return notificationRepository.insert(
        new NewNotification(recipient.id(), actor.id(), "follow", null, at));
  }

  private long like(Account actor, Account recipient, long workoutId, OffsetDateTime at) {
    return notificationRepository.insert(
        new NewNotification(recipient.id(), actor.id(), "like", workoutId, at));
  }

  private int count(Account recipient) {
    return dsl.fetchCount(NOTIFICATIONS, NOTIFICATIONS.RECIPIENT_ID.eq(recipient.id()));
  }

  private ResultActions list(Account account, String cursor) throws Exception {
    var request = get("/api/notifications").header(HttpHeaders.AUTHORIZATION, account.bearer());
    if (cursor != null) {
      request.param("cursor", cursor);
    }
    return mockMvc.perform(request);
  }

  private ResultActions unread(Account account) throws Exception {
    return mockMvc.perform(
        get("/api/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }

  private ResultActions read(Account account, List<Long> ids) throws Exception {
    return mockMvc.perform(
        post("/api/notifications/read")
            .header(HttpHeaders.AUTHORIZATION, account.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("ids", ids))));
  }

  private JsonNode json(ResultActions result) throws Exception {
    return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
  }
}
