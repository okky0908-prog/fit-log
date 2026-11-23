package com.okimoto.fitlog.follow;

import static com.okimoto.fitlog.jooq.Tables.NOTIFICATIONS;
import static com.okimoto.fitlog.jooq.Tables.USERS;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * フォロー（A-70〜A-73）・プロフィール（A-80）と、フォローによる閲覧の変化（docs/api.md「フォロー・プロフィール」、docs/test-plan.md
 * 4.1・5.8・5.9）。
 *
 * <p>リクエストの承認（A-75）はまだないので、承認済みのフォロワーがいる鍵アカウントは「鍵でないときにフォローしてから、鍵アカウントにする」で作る
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FollowIntegrationTest {

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
  class フォロー_A70 {

    @Test
    void 鍵アカウントでない人は_すぐフォロー中になり_followの通知が届く() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();

      follow(me, sato.username())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.followStatus").value("following"));

      profile(me, sato.username())
          .andExpect(jsonPath("$.followStatus").value("following"))
          .andExpect(jsonPath("$.followerCount").value(1));
      profile(me, me.username()).andExpect(jsonPath("$.followingCount").value(1));
      awaitNotifications(sato, List.of("follow"));
    }

    @Test
    void 鍵アカウントはリクエスト中になり_follow_requestの通知が届く_数には入らない() throws Exception {
      Account me = accounts.signup();
      Account locked = accounts.signup();
      accounts.makePrivate(locked);

      follow(me, locked.username())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.followStatus").value("requested"));

      profile(me, locked.username())
          .andExpect(jsonPath("$.followStatus").value("requested"))
          .andExpect(jsonPath("$.followerCount").value(0))
          .andExpect(jsonPath("$.canViewRecords").value(false));
      profile(me, me.username()).andExpect(jsonPath("$.followingCount").value(0));
      awaitNotifications(locked, List.of("follow_request"));
      unreadCount(locked).andExpect(jsonPath("$.followRequestCount").value(1));
    }

    @Test
    void もう一度フォローしても_今の状態を返し_通知は増えない() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      Account locked = accounts.signup();
      accounts.makePrivate(locked);
      follow(me, sato.username());
      follow(me, locked.username());
      awaitNotifications(sato, List.of("follow"));
      awaitNotifications(locked, List.of("follow_request"));

      follow(me, sato.username()).andExpect(jsonPath("$.followStatus").value("following"));
      follow(me, locked.username()).andExpect(jsonPath("$.followStatus").value("requested"));

      // 「起きないこと」は、決めた時間（1秒）待って確かめる（docs/test-plan.md 4.5）
      await()
          .during(Duration.ofSeconds(1))
          .atMost(Duration.ofSeconds(2))
          .untilAsserted(() -> {
            assertThat(notificationTypes(sato)).containsExactly("follow");
            assertThat(notificationTypes(locked)).containsExactly("follow_request");
          });
      profile(me, sato.username()).andExpect(jsonPath("$.followerCount").value(1));
    }

    @Test
    void フォロー中の相手が鍵アカウントにしても_フォロー中のまま記録を見られる() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      follow(me, sato.username());

      accounts.makePrivate(sato);

      profile(me, sato.username())
          .andExpect(jsonPath("$.followStatus").value("following"))
          .andExpect(jsonPath("$.canViewRecords").value(true));
      follow(me, sato.username()).andExpect(jsonPath("$.followStatus").value("following"));
    }

    @Test
    void 自分自身は400_大文字で書いても同じ() throws Exception {
      Account me = accounts.signup();

      for (String username : List.of(me.username(), me.username().toUpperCase(Locale.ROOT))) {
        follow(me, username)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CANNOT_FOLLOW_SELF"));
      }
      profile(me, me.username()).andExpect(jsonPath("$.followingCount").value(0));
    }

    @Test
    void ユーザー名の大文字と小文字は区別しない() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();

      follow(me, sato.username().toUpperCase(Locale.ROOT))
          .andExpect(jsonPath("$.followStatus").value("following"));

      profile(me, sato.username()).andExpect(jsonPath("$.followerCount").value(1));
    }
  }

  @Nested
  class 解除_A71 {

    @Test
    void フォロー中を解除すると_なしになり_followの通知が消える() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      follow(me, sato.username());
      awaitNotifications(sato, List.of("follow"));

      unfollow(me, sato.username())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.followStatus").value("none"));

      assertThat(notificationTypes(sato)).isEmpty();
      profile(me, sato.username())
          .andExpect(jsonPath("$.followStatus").value("none"))
          .andExpect(jsonPath("$.followerCount").value(0));
    }

    @Test
    void リクエストを取り消すと_なしになり_follow_requestの通知とリクエストの数が消える() throws Exception {
      Account me = accounts.signup();
      Account locked = accounts.signup();
      accounts.makePrivate(locked);
      follow(me, locked.username());
      awaitNotifications(locked, List.of("follow_request"));

      unfollow(me, locked.username()).andExpect(jsonPath("$.followStatus").value("none"));

      assertThat(notificationTypes(locked)).isEmpty();
      unreadCount(locked)
          .andExpect(jsonPath("$.unreadCount").value(0))
          .andExpect(jsonPath("$.followRequestCount").value(0));
    }

    @Test
    void 消すのはその人からの通知だけ_ほかの人のフォローの通知は残る() throws Exception {
      Account me = accounts.signup();
      Account other = accounts.signup();
      Account sato = accounts.signup();
      follow(me, sato.username());
      follow(other, sato.username());
      awaitNotifications(sato, List.of("follow", "follow"));

      unfollow(me, sato.username());

      assertThat(notificationTypes(sato)).containsExactly("follow");
    }

    @Test
    void フォローしていなくてもエラーにしない_何回呼んでも同じ() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();

      for (int i = 0; i < 2; i++) {
        unfollow(me, sato.username())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.followStatus").value("none"));
      }
    }

    @Test
    void フォローの直後に解除しても_通知は残らない() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();

      follow(me, sato.username());
      unfollow(me, sato.username());

      await()
          .during(Duration.ofSeconds(1))
          .atMost(Duration.ofSeconds(2))
          .untilAsserted(() -> assertThat(notificationTypes(sato)).isEmpty());
    }

    @Test
    void 自分自身は400() throws Exception {
      Account me = accounts.signup();

      unfollow(me, me.username())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("CANNOT_FOLLOW_SELF"));
    }
  }

  @Nested
  class フォロー一覧_A72_A73 {

    @Test
    void 承認済みだけを_フォローした新しい順に返し_見ている人から見た状態を付ける() throws Exception {
      Account owner = accounts.signup();
      Account viewer = accounts.signup();
      Account first = accounts.signup();
      Account second = accounts.signup();
      Account locked = accounts.signup();
      accounts.makePrivate(locked);
      bio(second, "毎朝走っています");
      follow(owner, first.username());
      follow(owner, second.username());
      follow(owner, viewer.username());
      follow(owner, locked.username()); // リクエスト中は出ない
      follow(viewer, second.username());
      follow(viewer, locked.username());

      following(viewer, owner.username(), null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items.length()").value(3))
          .andExpect(jsonPath("$.items[0].username").value(viewer.username()))
          .andExpect(jsonPath("$.items[0].me").value(true))
          .andExpect(jsonPath("$.items[0].followStatus").value("none"))
          .andExpect(jsonPath("$.items[1].username").value(second.username()))
          .andExpect(jsonPath("$.items[1].displayName").value("表示名"))
          .andExpect(jsonPath("$.items[1].iconUrl").isEmpty())
          .andExpect(jsonPath("$.items[1].isPrivate").value(false))
          .andExpect(jsonPath("$.items[1].bio").value("毎朝走っています"))
          .andExpect(jsonPath("$.items[1].followStatus").value("following"))
          .andExpect(jsonPath("$.items[1].me").value(false))
          .andExpect(jsonPath("$.items[2].username").value(first.username()))
          .andExpect(jsonPath("$.items[2].followStatus").value("none"))
          .andExpect(jsonPath("$.hasNext").value(false))
          .andExpect(jsonPath("$.nextCursor").isEmpty());

      followers(owner, second.username(), null)
          .andExpect(jsonPath("$.items.length()").value(2))
          .andExpect(jsonPath("$.items[0].username").value(viewer.username()))
          .andExpect(jsonPath("$.items[0].followStatus").value("following"))
          .andExpect(jsonPath("$.items[1].username").value(owner.username()))
          .andExpect(jsonPath("$.items[1].me").value(true));
      // 鍵アカウントへのリクエスト中は、フォロワー一覧にも出ない
      followers(locked, locked.username(), null).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void 見ている人がリクエスト中の人は_requestedになる() throws Exception {
      Account owner = accounts.signup();
      Account viewer = accounts.signup();
      Account sato = accounts.signup();
      follow(owner, sato.username());
      accounts.makePrivate(sato);
      follow(viewer, sato.username());

      following(viewer, owner.username(), null)
          .andExpect(jsonPath("$.items[0].username").value(sato.username()))
          .andExpect(jsonPath("$.items[0].isPrivate").value(true))
          .andExpect(jsonPath("$.items[0].followStatus").value("requested"));
    }

    @Test
    void 一度に20件ずつ返し_続きはカーソルで取る() throws Exception {
      Account owner = accounts.signup();
      List<String> names = new ArrayList<>();
      for (int i = 0; i < 21; i++) {
        Account follower = accounts.signup();
        follow(follower, owner.username());
        names.add(follower.username());
      }

      JsonNode first = json(followers(owner, owner.username(), null));
      assertThat(first.get("items").size()).isEqualTo(20);
      assertThat(first.get("items").get(0).get("username").asString()).isEqualTo(names.get(20));
      assertThat(first.get("hasNext").asBoolean()).isTrue();

      JsonNode second =
          json(followers(owner, owner.username(), first.get("nextCursor").asString()));
      assertThat(second.get("items").size()).isEqualTo(1);
      assertThat(second.get("items").get(0).get("username").asString()).isEqualTo(names.get(0));
      assertThat(second.get("hasNext").asBoolean()).isFalse();
    }

    @Test
    void 鍵アカウントの一覧は_本人と承認済みのフォロワーだけが見られ_ほかは403() throws Exception {
      Account locked = accounts.signup();
      Account follower = accounts.signup();
      follow(follower, locked.username());
      accounts.makePrivate(locked);
      Account requester = accounts.signup();
      follow(requester, locked.username());
      Account stranger = accounts.signup();

      for (Account viewer : List.of(locked, follower)) {
        following(viewer, locked.username(), null).andExpect(status().isOk());
        followers(viewer, locked.username(), null).andExpect(status().isOk());
      }
      for (Account viewer : List.of(requester, stranger)) {
        following(viewer, locked.username(), null)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PRIVATE_ACCOUNT"));
        followers(viewer, locked.username(), null)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PRIVATE_ACCOUNT"));
      }
    }

    @Test
    void カーソルが壊れていれば400() throws Exception {
      Account me = accounts.signup();

      followers(me, me.username(), "壊れた")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("cursor"));
    }

    /** 応答のキーは許可リストと同じ（docs/test-plan.md 4.2）。メールアドレスなどを出さない */
    @Test
    void 応答のキーは許可リストと同じ() throws Exception {
      Account me = accounts.signup();
      follow(accounts.signup(), me.username());

      JsonNode body = json(followers(me, me.username(), null));

      TreeSet<String> keys = new TreeSet<>();
      body.properties().forEach(entry -> keys.add(entry.getKey()));
      body.get("items").get(0).properties().forEach(entry -> keys.add("items[]." + entry.getKey()));
      assertThat(keys)
          .containsExactlyInAnyOrder(
              "items",
              "nextCursor",
              "hasNext",
              "items[].username",
              "items[].displayName",
              "items[].iconUrl",
              "items[].isPrivate",
              "items[].bio",
              "items[].followStatus",
              "items[].me");
    }
  }

  @Nested
  class プロフィール_A80 {

    @Test
    void 鍵アカウントでも_名前と自己紹介と数は返し_記録を見られるかを付ける() throws Exception {
      Account locked = accounts.signup();
      bio(locked, "週3回ジムに通っています");
      Account follower = accounts.signup();
      follow(follower, locked.username());
      follow(locked, follower.username());
      accounts.makePrivate(locked);
      Account requester = accounts.signup();
      follow(requester, locked.username());

      profile(requester, locked.username().toUpperCase(Locale.ROOT))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.username").value(locked.username()))
          .andExpect(jsonPath("$.displayName").value("表示名"))
          .andExpect(jsonPath("$.iconUrl").isEmpty())
          .andExpect(jsonPath("$.bio").value("週3回ジムに通っています"))
          .andExpect(jsonPath("$.isPrivate").value(true))
          .andExpect(jsonPath("$.followingCount").value(1))
          .andExpect(jsonPath("$.followerCount").value(1))
          .andExpect(jsonPath("$.me").value(false))
          .andExpect(jsonPath("$.followStatus").value("requested"))
          .andExpect(jsonPath("$.canViewRecords").value(false));
      profile(follower, locked.username())
          .andExpect(jsonPath("$.followStatus").value("following"))
          .andExpect(jsonPath("$.canViewRecords").value(true));
      profile(locked, locked.username())
          .andExpect(jsonPath("$.me").value(true))
          .andExpect(jsonPath("$.followStatus").value("none"))
          .andExpect(jsonPath("$.canViewRecords").value(true));
    }

    @Test
    void 鍵アカウントでない人の記録は_誰でも見られる() throws Exception {
      Account sato = accounts.signup();

      profile(accounts.signup(), sato.username())
          .andExpect(jsonPath("$.bio").isEmpty())
          .andExpect(jsonPath("$.followStatus").value("none"))
          .andExpect(jsonPath("$.canViewRecords").value(true));
    }

    /** 応答のキーは許可リストと同じ（docs/test-plan.md 4.2）。メールアドレスなどを出さない */
    @Test
    void 応答のキーは許可リストと同じ() throws Exception {
      Account sato = accounts.signup();

      JsonNode body = json(profile(accounts.signup(), sato.username()));

      TreeSet<String> keys = new TreeSet<>();
      body.properties().forEach(entry -> keys.add(entry.getKey()));
      assertThat(keys)
          .containsExactlyInAnyOrder(
              "username",
              "displayName",
              "iconUrl",
              "bio",
              "isPrivate",
              "followingCount",
              "followerCount",
              "me",
              "followStatus",
              "canViewRecords");
    }
  }

  @Test
  void いないユーザーは_どれも404() throws Exception {
    Account me = accounts.signup();
    String missing = "missing_" + System.nanoTime();

    for (ResultActions result :
        List.of(
            follow(me, missing),
            unfollow(me, missing),
            following(me, missing, null),
            followers(me, missing, null),
            profile(me, missing))) {
      result
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
          .andExpect(jsonPath("$.message").value("ユーザーが見つかりません"));
    }
  }

  @Test
  void ログインしていなければ_どれも401() throws Exception {
    String username = accounts.signup().username();

    mockMvc
        .perform(post("/api/users/" + username + "/follow"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(delete("/api/users/" + username + "/follow"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/users/" + username + "/following"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/users/" + username + "/followers"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/users/" + username)).andExpect(status().isUnauthorized());
  }

  /** フォローで閲覧が変わること（docs/test-plan.md 4.1②。見てはいけない側は「鍵アカウントの公開の記録を、リクエスト中の人が見る」） */
  @Nested
  class フォローと記録の閲覧 {

    @Test
    void 鍵アカウントの公開の記録は_リクエスト中の人には404_承認済みのフォロワーは見られる() throws Exception {
      Account locked = accounts.signup();
      Account follower = accounts.signup();
      follow(follower, locked.username());
      accounts.makePrivate(locked);
      Account requester = accounts.signup();
      follow(requester, locked.username());
      long id = workouts.of(locked.id()).save();

      detail(requester, id)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("WORKOUT_NOT_FOUND"));
      removeWorkout(requester, id)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("WORKOUT_NOT_FOUND"));
      detail(follower, id).andExpect(status().isOk());
      // 見られる他人の記録は 403（404 と取り違えない）
      removeWorkout(follower, id)
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("FORBIDDEN"));
      detail(locked, id).andExpect(status().isOk());
    }

    @Test
    void フォロワー限定の記録は_フォローすると見え_解除すると見えなくなる() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      long id = workouts.of(sato.id()).visibility("followers").save();
      detail(me, id).andExpect(status().isNotFound());

      follow(me, sato.username());
      detail(me, id).andExpect(status().isOk());

      unfollow(me, sato.username());
      detail(me, id).andExpect(status().isNotFound());
    }

    @Test
    void 逆向きのフォロー_相手からフォローされているだけでは_フォロワー限定の記録は見えない() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      long id = workouts.of(sato.id()).visibility("followers").save();

      follow(sato, me.username());

      detail(me, id).andExpect(status().isNotFound());
    }
  }

  // ===== 補助 =====

  private ResultActions follow(Account account, String username) throws Exception {
    return mockMvc.perform(
        post("/api/users/" + username + "/follow")
            .header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }

  private ResultActions unfollow(Account account, String username) throws Exception {
    return mockMvc.perform(
        delete("/api/users/" + username + "/follow")
            .header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }

  private ResultActions following(Account account, String username, String cursor)
      throws Exception {
    return list(account, username, "following", cursor);
  }

  private ResultActions followers(Account account, String username, String cursor)
      throws Exception {
    return list(account, username, "followers", cursor);
  }

  private ResultActions list(Account account, String username, String kind, String cursor)
      throws Exception {
    var request =
        get("/api/users/" + username + "/" + kind)
            .header(HttpHeaders.AUTHORIZATION, account.bearer());
    if (cursor != null) {
      request.param("cursor", cursor);
    }
    return mockMvc.perform(request);
  }

  private ResultActions profile(Account account, String username) throws Exception {
    return mockMvc.perform(
        get("/api/users/" + username).header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }

  private ResultActions unreadCount(Account account) throws Exception {
    return mockMvc.perform(
        get("/api/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }

  private ResultActions detail(Account account, long id) throws Exception {
    return mockMvc.perform(
        get("/api/workouts/" + id).header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }

  private ResultActions removeWorkout(Account account, long id) throws Exception {
    return mockMvc.perform(
        delete("/api/workouts/" + id).header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }

  /** 自己紹介を入れる（プロフィールの編集の API を作るまでは DB を直接変える） */
  private void bio(Account account, String value) {
    dsl.update(USERS).set(USERS.BIO, value).where(USERS.ID.eq(account.id())).execute();
  }

  /** 受け取った通知の種類（古い順） */
  private List<String> notificationTypes(Account recipient) {
    return dsl.select(NOTIFICATIONS.TYPE)
        .from(NOTIFICATIONS)
        .where(NOTIFICATIONS.RECIPIENT_ID.eq(recipient.id()))
        .orderBy(NOTIFICATIONS.ID)
        .fetch(NOTIFICATIONS.TYPE);
  }

  /** 通知は保存の確定後に別のスレッドで作るので、できるのを待つ */
  private void awaitNotifications(Account recipient, List<String> types) {
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(notificationTypes(recipient)).isEqualTo(types));
  }

  private JsonNode json(ResultActions result) throws Exception {
    return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
  }
}
