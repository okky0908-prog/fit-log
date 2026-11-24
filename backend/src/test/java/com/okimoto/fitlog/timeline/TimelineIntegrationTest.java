package com.okimoto.fitlog.timeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
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
import com.okimoto.fitlog.workout.calc.StoredSet;
import com.okimoto.fitlog.workout.repository.WorkoutRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
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
 * タイムライン（A-50〜A-53）とプロフィールの記録一覧（A-81）。一覧の1件の形（WorkoutCard）・ページング・閲覧の制御（docs/test-plan.md 4.1 ②）・N+1
 * がないこと（4.7）。
 *
 * <p>DB はテスト全体で共有するので、全体タイムラインに出す記録は、投稿日時を遠い未来（2998年）にして先頭に来るようにし、テストごとに時刻をずらす
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TimelineIntegrationTest {

  private static final OffsetDateTime FUTURE = OffsetDateTime.parse("2998-01-01T00:00:00+09:00");
  private static final AtomicLong MINUTES = new AtomicLong();

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

  /** ほかのどの記録よりも新しい投稿日時（呼ぶたびに1分ずつ進む） */
  private static OffsetDateTime newest() {
    return FUTURE.plusMinutes(MINUTES.incrementAndGet());
  }

  @Nested
  class フォロー中タイムライン_A50 {

    @Test
    void 一覧の1件の形() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      follow(me, sato);
      long id =
          workouts
              .of(sato.id())
              .performedOn(LocalDate.of(2026, 11, 14))
              .body("今日は脚の日！ #脚トレ")
              .weightReps("ベンチプレス", 60, 10, 3)
              .exercise(
                  "スクワット", List.of(weightReps("80", 8), weightReps("100", 5), weightReps("90", 6)))
              .exercise("腕立て伏せ", List.of(reps(20), reps(20), reps(15)))
              .timeDistance("ランニング", 1800, 5.0)
              .reps("腕立て伏せ", 10, 1)
              .reps("腕立て伏せ", 10, 1)
              .save();
      workoutRepository.insertImages(id, List.of("workouts/card.jpg"), FUTURE);

      timeline(me, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items", hasSize(1)))
          .andExpect(jsonPath("$.hasNext").value(false))
          .andExpect(jsonPath("$.nextCursor").value(nullValue()))
          .andExpect(jsonPath("$.items[0].id").value(id))
          .andExpect(jsonPath("$.items[0].author.username").value(sato.username()))
          .andExpect(jsonPath("$.items[0].author.iconUrl").value(nullValue()))
          .andExpect(jsonPath("$.items[0].performedOn").value("2026-11-14"))
          .andExpect(jsonPath("$.items[0].createdAt").value("2026-11-20T19:00:00+09:00"))
          .andExpect(jsonPath("$.items[0].editedAt").value(nullValue()))
          .andExpect(jsonPath("$.items[0].body").value("今日は脚の日！ #脚トレ"))
          .andExpect(jsonPath("$.items[0].visibility").value("public"))
          .andExpect(jsonPath("$.items[0].exerciseSummaries", hasSize(5)))
          .andExpect(jsonPath("$.items[0].exerciseSummaries[0].exerciseId").value(1))
          .andExpect(jsonPath("$.items[0].exerciseSummaries[0].name").value("ベンチプレス"))
          .andExpect(jsonPath("$.items[0].exerciseSummaries[0].recordType").value("weight_reps"))
          .andExpect(jsonPath("$.items[0].exerciseSummaries[0].personalBest").value(false))
          .andExpect(jsonPath("$.items[0].exerciseSummaries[0].setCount").value(3))
          .andExpect(jsonPath("$.items[0].exerciseSummaries[0].uniform").value(true))
          .andExpect(jsonPath("$.items[0].exerciseSummaries[0].weightKg").value(60.0))
          .andExpect(jsonPath("$.items[0].exerciseSummaries[0].reps").value(10))
          .andExpect(jsonPath("$.items[0].exerciseSummaries[0].totalReps").value(nullValue()))
          .andExpect(jsonPath("$.items[0].exerciseSummaries[1].uniform").value(false))
          .andExpect(jsonPath("$.items[0].exerciseSummaries[1].weightKg").value(100.0))
          .andExpect(jsonPath("$.items[0].exerciseSummaries[1].reps").value(5))
          .andExpect(jsonPath("$.items[0].exerciseSummaries[2].totalReps").value(55))
          .andExpect(jsonPath("$.items[0].exerciseSummaries[2].reps").value(nullValue()))
          .andExpect(jsonPath("$.items[0].exerciseSummaries[3].uniform").value(nullValue()))
          .andExpect(jsonPath("$.items[0].exerciseSummaries[3].durationSec").value(1800))
          .andExpect(jsonPath("$.items[0].exerciseSummaries[3].distanceKm").value(5.0))
          .andExpect(jsonPath("$.items[0].exerciseSummaries[3].paceSecPerKm").value(360))
          .andExpect(jsonPath("$.items[0].otherExerciseCount").value(1))
          .andExpect(jsonPath("$.items[0].images", hasSize(1)))
          .andExpect(jsonPath("$.items[0].images[0].url").value(startsWith("http")))
          .andExpect(jsonPath("$.items[0].images[0].sortOrder").value(1))
          .andExpect(jsonPath("$.items[0].likeCount").value(0))
          .andExpect(jsonPath("$.items[0].likedByMe").value(false))
          .andExpect(jsonPath("$.items[0].commentCount").value(0))
          .andExpect(jsonPath("$.items[0].mine").value(false))
          // カードに出さない項目は返さない
          .andExpect(jsonPath("$.items[0].durationMin").doesNotExist())
          .andExpect(jsonPath("$.items[0].exercises").doesNotExist());
    }

    @Test
    void 自分の記録も出し_20件ずつ続きを読み込める() throws Exception {
      Account me = accounts.signup();
      OffsetDateTime base = OffsetDateTime.parse("2026-11-20T10:00:00+09:00");
      List<Long> ids =
          IntStream.range(0, 21)
              .mapToObj(i -> workouts.of(me.id()).createdAt(base.plusMinutes(i)).save())
              .toList();

      JsonNode first = json(timeline(me, null).andExpect(jsonPath("$.items", hasSize(20))));
      assertThat(first.get("hasNext").asBoolean()).isTrue();
      assertThat(first.get("items").get(0).get("id").asLong()).isEqualTo(ids.get(20));
      assertThat(first.get("items").get(0).get("mine").asBoolean()).isTrue();

      timeline(me, first.get("nextCursor").asString())
          .andExpect(jsonPath("$.items", hasSize(1)))
          .andExpect(jsonPath("$.items[0].id").value(ids.get(0)))
          .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void フォローを解除すると出なくなる() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      follow(me, sato);
      workouts.of(sato.id()).visibility("followers").save();
      timeline(me, null).andExpect(jsonPath("$.items", hasSize(1)));

      unfollow(me, sato);

      timeline(me, null).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void 鍵アカウントにリクエスト中の人には_公開の記録も出ない() throws Exception {
      Account me = accounts.signup();
      Account locked = accounts.signup();
      accounts.makePrivate(locked);
      follow(me, locked);
      workouts.of(locked.id()).save();

      timeline(me, null).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void カーソルが壊れていれば400() throws Exception {
      Account me = accounts.signup();

      timeline(me, "壊れた")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.errors[0].field").value("cursor"));
    }
  }

  @Nested
  class 全体タイムライン_A51 {

    @Test
    void フォローしていない人の公開の記録も出し_見てはいけない記録は出ない() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      Account locked = accounts.signup();
      accounts.makePrivate(locked);
      follow(me, locked);
      OffsetDateTime at = newest();
      long publicOne = workouts.of(sato.id()).createdAt(at).save();
      workouts.of(sato.id()).createdAt(at).visibility("followers").save();
      workouts.of(locked.id()).createdAt(at).save();

      JsonNode page = json(all(me, null).andExpect(status().isOk()));

      assertThat(idsAt(page, at)).containsExactly(publicOne);
    }

    @Test
    void カーソルが壊れていれば400() throws Exception {
      Account me = accounts.signup();

      all(me, "壊れた").andExpect(status().isBadRequest());
    }
  }

  @Nested
  class 新しい投稿の件数_A52_A53 {

    @Test
    void sinceより後の_見てよい他人の記録を数える() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      Account tanaka = accounts.signup();
      follow(me, sato);
      long since = workouts.of(sato.id()).createdAt(newest()).save();
      workouts.of(sato.id()).createdAt(newest()).save();
      workouts.of(sato.id()).createdAt(newest()).visibility("private").save();
      workouts.of(tanaka.id()).createdAt(newest()).save();
      workouts.of(me.id()).createdAt(newest()).save();

      newCount(me, "/api/timeline/new-count", since)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.count").value(1));
      newCount(me, "/api/timeline/all/new-count", since)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    void 件数は101件あっても100で数えるのをやめる() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      follow(me, sato);
      long since = workouts.of(sato.id()).save();
      IntStream.range(0, 101).forEach(i -> workouts.of(sato.id()).save());

      newCount(me, "/api/timeline/new-count", since).andExpect(jsonPath("$.count").value(100));
    }

    @Test
    void sinceが数でなければ400() throws Exception {
      Account me = accounts.signup();

      mockMvc
          .perform(
              get("/api/timeline/new-count")
                  .param("since", "abc")
                  .header(HttpHeaders.AUTHORIZATION, me.bearer()))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }
  }

  @Nested
  class ユーザーの記録一覧_A81 {

    @Test
    void 実施日の新しい順に20件ずつ_続きを読み込める() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      LocalDate day = LocalDate.of(2026, 11, 1);
      List<Long> ids =
          IntStream.range(0, 21)
              .mapToObj(i -> workouts.of(sato.id()).performedOn(day.plusDays(i)).save())
              .toList();

      JsonNode first =
          json(userWorkouts(me, sato.username(), null).andExpect(jsonPath("$.items", hasSize(20))));
      assertThat(first.get("items").get(0).get("id").asLong()).isEqualTo(ids.get(20));

      userWorkouts(me, sato.username(), first.get("nextCursor").asString())
          .andExpect(jsonPath("$.items", hasSize(1)))
          .andExpect(jsonPath("$.items[0].id").value(ids.get(0)))
          .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void ちょうど20件なら_続きはない() throws Exception {
      Account me = accounts.signup();
      IntStream.range(0, 20).forEach(i -> workouts.of(me.id()).save());

      userWorkouts(me, me.username(), null)
          .andExpect(jsonPath("$.items", hasSize(20)))
          .andExpect(jsonPath("$.hasNext").value(false))
          .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void ユーザー名の大文字と小文字は区別しない() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      workouts.of(sato.id()).save();

      userWorkouts(me, sato.username().toUpperCase(java.util.Locale.ROOT), null)
          .andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void 鍵アカウントで見られなければ0件_エラーにしない() throws Exception {
      Account me = accounts.signup();
      Account locked = accounts.signup();
      accounts.makePrivate(locked);
      workouts.of(locked.id()).save();

      userWorkouts(me, locked.username(), null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items", hasSize(0)))
          .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void 自分の記録は_自分のみの記録も出る() throws Exception {
      Account me = accounts.signup();
      workouts.of(me.id()).visibility("private").save();

      userWorkouts(me, me.username(), null)
          .andExpect(jsonPath("$.items", hasSize(1)))
          .andExpect(jsonPath("$.items[0].mine").value(true));
    }

    @Test
    void 存在しないユーザーは404() throws Exception {
      Account me = accounts.signup();

      userWorkouts(me, "nobody_here", null)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void カーソルが壊れていれば400_タイムラインのカーソルは使えない() throws Exception {
      Account me = accounts.signup();
      workouts.of(me.id()).save();
      String timelineCursor =
          com.okimoto.fitlog.common.calc.Cursor.encode(OffsetDateTime.now(), 1L);

      userWorkouts(me, me.username(), timelineCursor)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("cursor"));
    }
  }

  /** 1件のときと1ページ分（20件）のときで、SQL の数が同じ（docs/test-plan.md 4.7） */
  @Nested
  class N1がないこと {

    @Test
    void フォロー中タイムラインとユーザーの記録一覧() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      follow(me, sato);
      saveRich(sato);

      int timelineOne =
          sqlCounter.count(() -> timeline(me, null).andExpect(jsonPath("$.items", hasSize(1))));
      int userOne =
          sqlCounter.count(() ->
              userWorkouts(me, sato.username(), null).andExpect(jsonPath("$.items", hasSize(1))));

      IntStream.range(0, 19).forEach(i -> saveRich(sato));

      assertThat(timelineOne).isPositive();
      assertThat(
              sqlCounter.count(
                  () -> timeline(me, null).andExpect(jsonPath("$.items", hasSize(20)))))
          .isEqualTo(timelineOne);
      assertThat(
              sqlCounter.count(() ->
                  userWorkouts(me, sato.username(), null)
                      .andExpect(jsonPath("$.items", hasSize(20)))))
          .isEqualTo(userOne);
      // 全体タイムラインは、ほかのテストの記録も入り1件だけにできないので、1ページ分（20件）のときを、フォロー中の1件のときと比べる（同じ作りの SQL）
      assertThat(sqlCounter.count(() -> all(me, null).andExpect(jsonPath("$.items", hasSize(20)))))
          .isEqualTo(timelineOne);
    }

    /** 種目6つ（「ほか1種目」）・画像2枚の記録 */
    private long saveRich(Account account) {
      long id =
          workouts
              .of(account.id())
              .createdAt(newest())
              .weightReps("ベンチプレス", 60, 10, 3)
              .reps("腕立て伏せ", 20, 3)
              .timeDistance("ランニング", 1800, 5.0)
              .weightReps("スクワット", 100, 5, 3)
              .reps("腕立て伏せ", 10, 2)
              .reps("腕立て伏せ", 10, 2)
              .save();
      workoutRepository.insertImages(id, List.of("workouts/1.jpg", "workouts/2.jpg"), FUTURE);
      return id;
    }
  }

  private static StoredSet weightReps(String kg, int reps) {
    return new StoredSet(new BigDecimal(kg).setScale(3), reps, null, null);
  }

  private static StoredSet reps(int reps) {
    return new StoredSet(null, reps, null, null);
  }

  /** 投稿日時が at の記録の ID（ページの並び順のとおり） */
  private static List<Long> idsAt(JsonNode page, OffsetDateTime at) {
    return page.get("items")
        .valueStream()
        .filter(item -> OffsetDateTime.parse(item.get("createdAt").asString()).isEqual(at))
        .map(item -> item.get("id").asLong())
        .toList();
  }

  private JsonNode json(ResultActions result) throws Exception {
    return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
  }

  private void follow(Account me, Account target) throws Exception {
    mockMvc
        .perform(
            post("/api/users/" + target.username() + "/follow")
                .header(HttpHeaders.AUTHORIZATION, me.bearer()))
        .andExpect(status().isOk());
  }

  private void unfollow(Account me, Account target) throws Exception {
    mockMvc
        .perform(
            delete("/api/users/" + target.username() + "/follow")
                .header(HttpHeaders.AUTHORIZATION, me.bearer()))
        .andExpect(status().isOk());
  }

  private ResultActions timeline(Account account, String cursor) throws Exception {
    return list(account, "/api/timeline", cursor);
  }

  private ResultActions all(Account account, String cursor) throws Exception {
    return list(account, "/api/timeline/all", cursor);
  }

  private ResultActions userWorkouts(Account account, String username, String cursor)
      throws Exception {
    return list(account, "/api/users/" + username + "/workouts", cursor);
  }

  private ResultActions list(Account account, String path, String cursor) throws Exception {
    var request = get(path).header(HttpHeaders.AUTHORIZATION, account.bearer());
    if (cursor != null) {
      request.param("cursor", cursor);
    }
    return mockMvc.perform(request);
  }

  private ResultActions newCount(Account account, String path, long since) throws Exception {
    return mockMvc.perform(
        get(path)
            .param("since", String.valueOf(since))
            .header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }
}
