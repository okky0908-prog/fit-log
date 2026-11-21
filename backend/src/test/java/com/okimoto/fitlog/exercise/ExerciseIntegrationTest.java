package com.okimoto.fitlog.exercise;

import static com.okimoto.fitlog.jooq.Tables.EXERCISES;
import static com.okimoto.fitlog.jooq.Tables.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.okimoto.fitlog.auth.service.JwtService;
import com.okimoto.fitlog.exercise.calc.ExerciseRules;
import com.okimoto.fitlog.exercise.repository.ExerciseRepository;
import com.okimoto.fitlog.exercise.repository.NewExercise;
import com.okimoto.fitlog.support.TestAccounts;
import com.okimoto.fitlog.support.TestcontainersConfiguration;
import com.okimoto.fitlog.support.WorkoutFixture;
import com.okimoto.fitlog.workout.calc.StoredSet;
import com.okimoto.fitlog.workout.repository.WorkoutRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * 種目の API（A-30〜A-34）を、API から DB まで通して確かめる（docs/api.md「種目・テンプレート」、docs/test-plan.md 5.1・5.12）。DB
 * はテスト全体で共有するので、テストごとに新しいユーザーを作る
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExerciseIntegrationTest {

  private static final String NAME_MESSAGE = "種目の名前は1〜30文字で入力してください";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private DSLContext dsl;
  @Autowired private JwtService jwtService;
  @Autowired private WorkoutRepository workoutRepository;

  @Nested
  class 検索_A30 {

    @Test
    void 名前の部分一致と部位で絞り込み_自分の種目を含め_他人の種目は含めない() throws Exception {
      String me = signup();
      String other = signup();
      create(me, "ケーブルプレス", "shoulders", "weight_reps").andExpect(status().isCreated());
      create(other, "他人のプレス", "shoulders", "weight_reps").andExpect(status().isCreated());

      // 半角カナ・前後の空白は、そろえてから比べる
      search(me, "?q= ﾌﾟﾚｽ &bodyPart=shoulders")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[*].name").value(contains("ショルダープレス", "ダンベルショルダープレス", "ケーブルプレス")))
          .andExpect(jsonPath("$[2].custom").value(true))
          .andExpect(jsonPath("$[2].bodyPart").value("shoulders"))
          .andExpect(jsonPath("$[2].recordType").value("weight_reps"))
          .andExpect(jsonPath("$[2].isRunning").value(false));
    }

    @Test
    void 空白だけの検索の文字は_絞り込まない() throws Exception {
      search(signup(), "?q=  ").andExpect(jsonPath("$.length()").value(58));
    }

    @Test
    void 検索の文字は30文字まで_31文字は400() throws Exception {
      String me = signup();
      search(me, "?q=" + "あ".repeat(30))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").isEmpty());

      search(me, "?q=" + "あ".repeat(31))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.errors[0].field").value("q"))
          .andExpect(jsonPath("$.errors[0].message").value("検索する文字は30文字以内で入力してください"));
    }

    @Test
    void ない部位で絞り込むと400() throws Exception {
      search(signup(), "?bodyPart=neck")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("bodyPart"))
          .andExpect(jsonPath("$.errors[0].message").value("部位の指定が正しくありません"));
    }
  }

  @Nested
  class 追加_A32 {

    @Test
    void 追加すると201で種目を返し_検索に出る() throws Exception {
      String me = signup();

      create(me, "  ブルガリアンスクワット ", "legs", "weight_reps")
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").isNumber())
          .andExpect(jsonPath("$.name").value("ブルガリアンスクワット"))
          .andExpect(jsonPath("$.bodyPart").value("legs"))
          .andExpect(jsonPath("$.recordType").value("weight_reps"))
          .andExpect(jsonPath("$.isRunning").value(false))
          .andExpect(jsonPath("$.custom").value(true));

      search(me, "?q=ブルガリアン").andExpect(jsonPath("$[*].name").value(contains("ブルガリアンスクワット")));
    }

    @Test
    void METsは部位と記録方式の標準の値になる() throws Exception {
      String me = signup();

      long cardio = idOf(create(me, "エアロバイク自作", "cardio", "time_distance"));
      long plank = idOf(create(me, "体幹の自作", "abs", "time_distance"));
      long reps = idOf(create(me, "自重の自作", "abs", "reps"));

      assertThat(mets(cardio)).isEqualByComparingTo("6.0");
      assertThat(mets(plank)).isEqualByComparingTo("3.5");
      assertThat(mets(reps)).isEqualByComparingTo("3.8");
    }

    @Test
    void 名前は30文字まで_31文字と空は400() throws Exception {
      String me = signup();
      create(me, "あ".repeat(30), "legs", "reps").andExpect(status().isCreated());

      for (String name : new String[] {"い".repeat(31), " ", ""}) {
        create(me, name, "legs", "reps")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("name"))
            .andExpect(jsonPath("$.errors[0].message").value(NAME_MESSAGE));
      }
    }

    @Test
    void 正規化で30文字を超える名前は400() throws Exception {
      // 「㌔」は NFKC で「キロ」の2文字になる。16文字の名前が32文字のキーになり、DB の列に入らない
      create(signup(), "㌔".repeat(16), "legs", "reps")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("name"))
          .andExpect(jsonPath("$.errors[0].message").value(NAME_MESSAGE));
    }

    @Test
    void 部位と記録方式がない_ない値なら400() throws Exception {
      String body = "{\"name\":\"種目\",\"bodyPart\":\"neck\"}";

      send(post("/api/exercises"), signup(), body)
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("$.errors[*].field").value(containsInAnyOrder("bodyPart", "recordType")))
          .andExpect(
              jsonPath("$.errors[*].message")
                  .value(containsInAnyOrder("部位を選んでください", "記録方式を選んでください")));
    }

    @Test
    void 用意された種目と同じ名前は_全角半角の違いだけでも409() throws Exception {
      create(signup(), "ﾍﾞﾝﾁﾌﾟﾚｽ", "chest", "weight_reps")
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("EXERCISE_NAME_TAKEN"))
          .andExpect(jsonPath("$.errors[0].field").value("name"))
          .andExpect(jsonPath("$.errors[0].message").value("同じ名前の種目があります"));
    }

    @Test
    void 自分の種目と同じ名前は_大文字小文字の違いだけでも409_他人の種目と同じ名前は作れる() throws Exception {
      String me = signup();
      String other = signup();
      create(me, "Cable Fly", "chest", "weight_reps").andExpect(status().isCreated());

      create(me, "CABLE FLY", "back", "reps")
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("EXERCISE_NAME_TAKEN"));
      create(other, "Cable Fly", "chest", "weight_reps").andExpect(status().isCreated());
    }

    @Test
    void 自分専用の種目は100件まで_100件あると409() throws Exception {
      String me = signup();
      insertCustoms(me, 99);

      create(me, "100件目", "legs", "reps").andExpect(status().isCreated());
      create(me, "101件目", "legs", "reps")
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("EXERCISE_LIMIT_EXCEEDED"));
    }

    @Test
    void 残り1件のときに同時に2件追加しても_1件だけ作られる() throws Exception {
      String me = signup();
      insertCustoms(me, 99);
      CountDownLatch start = new CountDownLatch(1);

      List<Integer> statuses = new ArrayList<>();
      try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
        List<Future<Integer>> results = new ArrayList<>();
        for (String name : new String[] {"同時A", "同時B"}) {
          results.add(
              executor.submit(() -> {
                start.await();
                return create(me, name, "legs", "reps").andReturn().getResponse().getStatus();
              }));
        }
        start.countDown();
        for (Future<Integer> result : results) {
          statuses.add(result.get());
        }
      }

      assertThat(statuses).containsExactlyInAnyOrder(201, 409);
      assertThat(dsl.fetchCount(EXERCISES, EXERCISES.OWNER_ID.eq(userId(me)))).isEqualTo(100);
    }
  }

  @Nested
  class 変更_A33 {

    @Test
    void 名前と部位を変える_記録方式は変わらず_METsは部位に合わせて変わる() throws Exception {
      String me = signup();
      long id = idOf(create(me, "ステップ台", "legs", "time_distance"));

      update(me, id, "ステップ台（有酸素）", "cardio")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(id))
          .andExpect(jsonPath("$.name").value("ステップ台（有酸素）"))
          .andExpect(jsonPath("$.bodyPart").value("cardio"))
          .andExpect(jsonPath("$.recordType").value("time_distance"))
          .andExpect(jsonPath("$.custom").value(true));
      assertThat(mets(id)).isEqualByComparingTo("6.0");
    }

    @Test
    void 自分自身と同じ名前_大文字小文字だけ変える_は変えられる() throws Exception {
      String me = signup();
      long id = idOf(create(me, "cable fly", "chest", "weight_reps"));

      update(me, id, "Cable Fly", "chest")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.name").value("Cable Fly"));
    }

    @Test
    void 用意された種目か自分のほかの種目と同じ名前は409() throws Exception {
      String me = signup();
      long id = idOf(create(me, "種目A", "chest", "weight_reps"));
      create(me, "種目B", "chest", "weight_reps").andExpect(status().isCreated());

      update(me, id, "ベンチプレス", "chest")
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("EXERCISE_NAME_TAKEN"));
      update(me, id, "種目Ｂ", "chest")
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("EXERCISE_NAME_TAKEN"));
    }

    @Test
    void 入力のチェックは追加と同じ() throws Exception {
      String me = signup();
      long id = idOf(create(me, "種目", "chest", "weight_reps"));

      send(put("/api/exercises/" + id), me, "{\"name\":\"\"}")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("name", "bodyPart")));
    }

    @Test
    void 他人の種目_用意された種目_ない種目は404() throws Exception {
      String me = signup();
      long othersId = idOf(create(signup(), "他人の種目", "chest", "weight_reps"));

      for (long id : new long[] {othersId, 1L, Long.MAX_VALUE}) {
        update(me, id, "新しい名前", "chest")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("EXERCISE_NOT_FOUND"));
      }
    }
  }

  @Nested
  class 最近使った種目_A31 {

    @Test
    void 自分の記録で使った種目を10件まで_検索と同じ形で返す() throws Exception {
      String me = signup();
      long custom = idOf(create(me, "最近の自分の種目", "legs", "reps"));
      WorkoutFixture.Builder workout =
          workouts().of(userId(me)).exercise(custom, List.of(new StoredSet(null, 10, null, null)));
      for (String name :
          List.of(
              "ベンチプレス",
              "インクラインベンチプレス",
              "ダンベルプレス",
              "ダンベルフライ",
              "チェストプレス",
              "ケーブルクロスオーバー",
              "ディップス",
              "腕立て伏せ",
              "デッドリフト",
              "ベントオーバーロウ")) {
        workout.reps(name, 10, 1);
      }
      workout.save();

      recent(me)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(10))
          .andExpect(jsonPath("$[0].id").value(custom))
          .andExpect(jsonPath("$[0].name").value("最近の自分の種目"))
          .andExpect(jsonPath("$[0].bodyPart").value("legs"))
          .andExpect(jsonPath("$[0].recordType").value("reps"))
          .andExpect(jsonPath("$[0].isRunning").value(false))
          .andExpect(jsonPath("$[0].custom").value(true))
          .andExpect(jsonPath("$[1].name").value("ベンチプレス"))
          .andExpect(jsonPath("$[1].custom").value(false))
          .andExpect(jsonPath("$[9].name").value("デッドリフト"));
    }

    @Test
    void 記録がなければ空() throws Exception {
      recent(signup()).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void ログインしていなければ401() throws Exception {
      mockMvc.perform(get("/api/exercises/recent")).andExpect(status().isUnauthorized());
    }
  }

  @Nested
  class 削除_A34 {

    @Test
    void 消すと204で_検索に出なくなり_もう一度消すと404() throws Exception {
      String me = signup();
      long id = idOf(create(me, "消す種目", "chest", "weight_reps"));

      remove(me, id).andExpect(status().isNoContent());

      search(me, "?q=消す種目").andExpect(jsonPath("$").isEmpty());
      remove(me, id)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("EXERCISE_NOT_FOUND"));
    }

    @Test
    void 記録で使っている種目は409で消えず_記録を消せば消せる() throws Exception {
      String me = signup();
      long id = idOf(create(me, "記録で使う種目", "legs", "reps"));
      long workoutId =
          workouts()
              .of(userId(me))
              .exercise(id, List.of(new StoredSet(null, 10, null, null)))
              .save();

      remove(me, id)
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("EXERCISE_IN_USE"))
          .andExpect(jsonPath("$.message").value("記録で使っている種目は削除できません"));
      search(me, "?q=記録で使う種目").andExpect(jsonPath("$[*].name").value(hasItem("記録で使う種目")));

      mockMvc
          .perform(
              delete("/api/workouts/" + workoutId)
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + me))
          .andExpect(status().isNoContent());
      remove(me, id).andExpect(status().isNoContent());
    }

    @Test
    void 種目の削除と_その種目を使う記録の保存が同時に来ても_500にならない() throws Exception {
      String me = signup();
      // 割り込みが起きる順番は毎回変わるので、何回か繰り返す
      for (int round = 0; round < 10; round++) {
        long id = idOf(create(me, "同時に使う種目" + round, "legs", "reps"));
        CountDownLatch start = new CountDownLatch(1);
        int removed;
        int saved;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
          Future<Integer> removal =
              executor.submit(() -> {
                start.await();
                return remove(me, id).andReturn().getResponse().getStatus();
              });
          Future<Integer> save =
              executor.submit(() -> {
                start.await();
                return createWorkout(me, id).andReturn().getResponse().getStatus();
              });
          start.countDown();
          removed = removal.get();
          saved = save.get();
        }

        // 先に保存されたなら削除は 409、先に削除されたなら保存は 400（種目が見つからない）
        assertThat(List.of(removed, saved))
            .as("%d 回目", round)
            .isIn(List.of(409, 201), List.of(204, 400));
      }
    }

    @Test
    void 他人の種目と用意された種目は404で_消えない() throws Exception {
      String me = signup();
      String other = signup();
      long othersId = idOf(create(other, "他人の種目", "chest", "weight_reps"));

      remove(me, othersId).andExpect(status().isNotFound());
      remove(me, 1L).andExpect(status().isNotFound());

      search(other, "?q=他人の種目").andExpect(jsonPath("$[*].name").value(hasItem("他人の種目")));
      search(me, "").andExpect(jsonPath("$[*].name").value(hasItem("ベンチプレス")));
      search(me, "").andExpect(jsonPath("$[*].name").value(not(hasItem("他人の種目"))));
    }
  }

  @Test
  void ログインしていなければ_どの操作も401() throws Exception {
    mockMvc.perform(post("/api/exercises")).andExpect(status().isUnauthorized());
    mockMvc.perform(put("/api/exercises/1")).andExpect(status().isUnauthorized());
    mockMvc.perform(delete("/api/exercises/1")).andExpect(status().isUnauthorized());
  }

  @Test
  void トークンが有効でもユーザーがいなければ_追加は401で_種目は作られない() throws Exception {
    // 退会した直後で、アクセストークンの期限（15分）がまだ残っているとき
    String token = jwtService.issue(Long.MAX_VALUE);

    create(token, "退会した人の種目", "legs", "reps")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    assertThat(dsl.fetchCount(EXERCISES, EXERCISES.NAME.eq("退会した人の種目"))).isZero();
  }

  @Test
  void トークンが有効でもユーザーがいなければ_削除は401() throws Exception {
    String token = jwtService.issue(Long.MAX_VALUE);

    remove(token, 1L)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  // ===== 補助 =====

  private ResultActions search(String token, String query) throws Exception {
    return mockMvc.perform(
        get("/api/exercises" + query).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
  }

  private ResultActions recent(String token) throws Exception {
    return mockMvc.perform(
        get("/api/exercises/recent").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
  }

  /** その種目を1つだけ使う記録を、API で作る */
  private ResultActions createWorkout(String token, long exerciseId) throws Exception {
    Map<String, Object> data =
        Map.of(
            "performedOn",
            "2026-11-01",
            "visibility",
            "public",
            "weightUnit",
            "kg",
            "distanceUnit",
            "km",
            "exercises",
            List.of(Map.of("exerciseId", exerciseId, "sets", List.of(Map.of("reps", 10)))));
    return mockMvc.perform(
        multipart("/api/workouts")
            .file(
                new MockMultipartFile(
                    "data",
                    "",
                    MediaType.APPLICATION_JSON_VALUE,
                    objectMapper.writeValueAsBytes(data)))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
  }

  private WorkoutFixture workouts() {
    return new WorkoutFixture(dsl, workoutRepository);
  }

  private ResultActions create(String token, String name, String bodyPart, String recordType)
      throws Exception {
    return send(
        post("/api/exercises"),
        token,
        objectMapper.writeValueAsString(new Create(name, bodyPart, recordType)));
  }

  private ResultActions update(String token, long id, String name, String bodyPart)
      throws Exception {
    return send(
        put("/api/exercises/" + id),
        token,
        objectMapper.writeValueAsString(new Update(name, bodyPart)));
  }

  private ResultActions remove(String token, long id) throws Exception {
    return mockMvc.perform(
        delete("/api/exercises/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
  }

  private ResultActions send(MockHttpServletRequestBuilder request, String token, String body)
      throws Exception {
    return mockMvc.perform(
        request
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private long idOf(ResultActions created) throws Exception {
    String body =
        created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(body).get("id").asLong();
  }

  private BigDecimal mets(long id) {
    return dsl.select(EXERCISES.METS)
        .from(EXERCISES)
        .where(EXERCISES.ID.eq(id))
        .fetchSingle()
        .value1();
  }

  /** 上限の手前まで、DB に直接入れる（API を99回呼ぶと遅いため） */
  private void insertCustoms(String token, int count) throws Exception {
    long owner = userId(token);
    for (int i = 1; i <= count; i++) {
      String name = "既存" + i;
      exerciseRepository.insertCustom(
          new NewExercise(
              owner,
              name,
              ExerciseRules.nameKey(name),
              "legs",
              "reps",
              new BigDecimal("3.8"),
              OffsetDateTime.now()));
    }
  }

  private long userId(String token) throws Exception {
    String body =
        mockMvc
            .perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String username = objectMapper.readTree(body).get("username").asString();
    return dsl.select(USERS.ID)
        .from(USERS)
        .where(USERS.USERNAME.eq(username))
        .fetchSingle()
        .value1();
  }

  /** 新しいユーザーを登録し、アクセストークンを返す */
  private String signup() throws Exception {
    return new TestAccounts(mockMvc, objectMapper, dsl).signup().token();
  }

  private record Create(String name, String bodyPart, String recordType) {}

  private record Update(String name, String bodyPart) {}
}
