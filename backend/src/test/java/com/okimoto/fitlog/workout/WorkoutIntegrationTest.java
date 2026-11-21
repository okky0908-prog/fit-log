package com.okimoto.fitlog.workout;

import static com.okimoto.fitlog.jooq.Tables.WORKOUTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.okimoto.fitlog.common.calc.JapanTime;
import com.okimoto.fitlog.exercise.calc.ExerciseRules;
import com.okimoto.fitlog.exercise.repository.ExerciseRepository;
import com.okimoto.fitlog.exercise.repository.NewExercise;
import com.okimoto.fitlog.support.TestAccounts;
import com.okimoto.fitlog.support.TestAccounts.Account;
import com.okimoto.fitlog.support.TestcontainersConfiguration;
import com.okimoto.fitlog.support.WorkoutFixture;
import com.okimoto.fitlog.workout.repository.WorkoutRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 記録の作成（A-20）と詳細（A-21）を、API から DB まで通して確かめる（docs/api.md「記録」、docs/test-plan.md 4.1・4.2・5.1・5.2） */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkoutIntegrationTest {

  private static final long BENCH_PRESS = 1L;
  private static final long PUSH_UP = 8L;
  private static final long RUNNING = 48L;

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DSLContext dsl;
  @Autowired private WorkoutRepository workoutRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private Clock clock;

  private TestAccounts accounts;
  private WorkoutFixture fixture;

  @BeforeEach
  void setUp() {
    accounts = new TestAccounts(mockMvc, objectMapper, dsl);
    fixture = new WorkoutFixture(dsl, workoutRepository);
  }

  @Nested
  class 作成_A20 {

    @Test
    void 作成すると201で記録を返し_lbとマイルはkgとkmにして保存する() throws Exception {
      Account me = accounts.signup();
      Map<String, Object> data = valid();
      data.put("body", "  脚の日！\r\n#脚トレ  ");
      data.put("durationMin", 75);
      data.put("visibility", "followers");
      data.put("weightUnit", "lb");
      data.put("distanceUnit", "mi");
      data.put(
          "exercises",
          List.of(
              exercise(BENCH_PRESS, set(135, 10), set(0.5, 0)),
              exercise(RUNNING, Map.of("durationSec", 1800, "distance", 3.1)),
              exercise(RUNNING, Map.of("durationSec", 600))));

      String body =
          create(me, data)
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.author.username").value(me.username()))
              .andExpect(jsonPath("$.author.displayName").value("表示名"))
              .andExpect(jsonPath("$.author.iconUrl").isEmpty())
              .andExpect(jsonPath("$.author.isPrivate").value(false))
              .andExpect(jsonPath("$.performedOn").value(today()))
              .andExpect(jsonPath("$.createdAt").value(org.hamcrest.Matchers.endsWith("+09:00")))
              .andExpect(jsonPath("$.editedAt").isEmpty())
              .andExpect(jsonPath("$.body").value("脚の日！\n#脚トレ"))
              .andExpect(jsonPath("$.durationMin").value(75))
              .andExpect(jsonPath("$.visibility").value("followers"))
              .andExpect(jsonPath("$.exercises[0].exerciseId").value(BENCH_PRESS))
              .andExpect(jsonPath("$.exercises[0].name").value("ベンチプレス"))
              .andExpect(jsonPath("$.exercises[0].bodyPart").value("chest"))
              .andExpect(jsonPath("$.exercises[0].recordType").value("weight_reps"))
              .andExpect(jsonPath("$.exercises[0].isRunning").value(false))
              .andExpect(jsonPath("$.exercises[0].personalBest").value(false))
              .andExpect(jsonPath("$.exercises[0].sets[0].weightKg").value(61.235))
              .andExpect(jsonPath("$.exercises[0].sets[0].reps").value(10))
              .andExpect(jsonPath("$.exercises[0].sets[0].durationSec").isEmpty())
              .andExpect(jsonPath("$.exercises[0].sets[0].distanceKm").isEmpty())
              .andExpect(jsonPath("$.exercises[0].sets[0].personalBest").value(false))
              .andExpect(jsonPath("$.exercises[0].sets[1].weightKg").value(0.227))
              .andExpect(jsonPath("$.exercises[0].sets[1].reps").value(0))
              .andExpect(jsonPath("$.exercises[1].isRunning").value(true))
              .andExpect(jsonPath("$.exercises[1].sets[0].durationSec").value(1800))
              .andExpect(jsonPath("$.exercises[1].sets[0].distanceKm").value(4.989))
              .andExpect(jsonPath("$.exercises[2].sets[0].distanceKm").isEmpty())
              .andExpect(jsonPath("$.images").isEmpty())
              .andExpect(jsonPath("$.likeCount").value(0))
              .andExpect(jsonPath("$.likedByMe").value(false))
              .andExpect(jsonPath("$.commentCount").value(0))
              .andExpect(jsonPath("$.mine").value(true))
              .andReturn()
              .getResponse()
              .getContentAsString();

      // 詳細で取り直しても同じ
      long id = objectMapper.readTree(body).get("id").asLong();
      String detail =
          detail(me, id).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
      assertThat(objectMapper.readTree(detail)).isEqualTo(objectMapper.readTree(body));
    }

    @Test
    void ひとことと時間は省略できる() throws Exception {
      Map<String, Object> data = valid();
      data.remove("body");
      data.remove("durationMin");

      create(accounts.signup(), data)
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.body").value(""))
          .andExpect(jsonPath("$.durationMin").isEmpty());
    }

    @Test
    void 入力のエラーは_項目をまたいでまとめて返す() throws Exception {
      Account me = accounts.signup();
      long othersExercise = customExercise(accounts.signup(), "他人の種目");
      Map<String, Object> data = valid();
      data.put("performedOn", JapanTime.today(clock).plusDays(2).toString());
      data.put("body", "あ".repeat(281));
      data.put("durationMin", 0);
      data.put("visibility", "friends");
      data.put(
          "exercises",
          List.of(
              exercise(othersExercise, set(60, 10)),
              exercise(
                  BENCH_PRESS, set(60.1, 10), Map.of("weight", 60, "reps", 10, "durationSec", 60)),
              exercise(PUSH_UP)));

      create(me, data)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(
              jsonPath("$.errors[*].field")
                  .value(
                      containsInAnyOrder(
                          "performedOn",
                          "body",
                          "durationMin",
                          "visibility",
                          "exercises[0].exerciseId",
                          "exercises[1].sets[0].weight",
                          "exercises[1].sets[1].durationSec",
                          "exercises[2].sets")))
          .andExpect(
              jsonPath("$.errors[*].message")
                  .value(
                      containsInAnyOrder(
                          "未来の日付は選べません",
                          "ひとことは280文字以内で入力してください",
                          "トレーニング時間は1〜1440分で入力してください",
                          "公開範囲を選んでください",
                          "種目が見つかりません",
                          "重量は0.25〜500kgの範囲で、0.25kg刻みで入力してください",
                          "この種目では入力できません",
                          "セットは1〜20件で入力してください")));
      assertThat(dsl.fetchCount(WORKOUTS, WORKOUTS.USER_ID.eq(me.id()))).isZero();
    }

    @Test
    void 必須の項目がなければ400() throws Exception {
      create(accounts.signup(), Map.of())
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("$.errors[*].field")
                  .value(
                      containsInAnyOrder(
                          "performedOn", "visibility", "weightUnit", "distanceUnit", "exercises")))
          .andExpect(
              jsonPath("$.errors[*].message")
                  .value(
                      containsInAnyOrder(
                          "実施日を入力してください",
                          "公開範囲を選んでください",
                          "重量の単位を選んでください",
                          "距離の単位を選んでください",
                          "種目は1〜20件で入力してください")));
    }

    @Test
    void 今日の日付と_ひとこと280文字と_トレーニング時間1440分は作れる() throws Exception {
      Map<String, Object> data = valid();
      data.put("body", "あ".repeat(280));
      data.put("durationMin", 1440);

      create(accounts.signup(), data).andExpect(status().isCreated());
    }

    @Test
    void 種目とセットは20件まで_21件は400() throws Exception {
      Account me = accounts.signup();
      Map<String, Object> twenty = valid();
      twenty.put(
          "exercises",
          Collections.nCopies(
              20, exercise(PUSH_UP, Collections.nCopies(20, reps(10)).toArray(Map[]::new))));
      create(me, twenty).andExpect(status().isCreated());

      Map<String, Object> tooMany = valid();
      List<Object> exercises =
          new ArrayList<>(Collections.nCopies(20, exercise(PUSH_UP, reps(10))));
      exercises.add(exercise(PUSH_UP, Collections.nCopies(21, reps(10)).toArray(Map[]::new)));
      tooMany.put("exercises", exercises);
      create(me, tooMany)
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("$.errors[*].field")
                  .value(containsInAnyOrder("exercises", "exercises[20].sets")));
    }

    @Test
    void 種目0件_セットの項目なし_トレーニング時間1441分と小数は400() throws Exception {
      Account me = accounts.signup();
      Map<String, Object> noExercises = valid();
      noExercises.put("exercises", List.of());
      create(me, noExercises)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("exercises")));

      Map<String, Object> noSets = valid();
      noSets.put("exercises", List.of(Map.of("exerciseId", PUSH_UP)));
      create(me, noSets)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("exercises[0].sets")));

      for (Object minutes : new Object[] {1441, 1.5}) {
        Map<String, Object> data = valid();
        data.put("durationMin", minutes);
        create(me, data)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("durationMin")));
      }
    }

    @Test
    void 種目やセットの中身がnullでも_500にせず400() throws Exception {
      Map<String, Object> data = valid();
      List<Object> exercises = new ArrayList<>();
      exercises.add(null);
      List<Object> sets = new ArrayList<>();
      sets.add(null);
      exercises.add(new LinkedHashMap<>(Map.of("sets", sets)));
      data.put("exercises", exercises);

      create(accounts.signup(), data)
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("$.errors[*].field")
                  .value(
                      containsInAnyOrder(
                          "exercises[0]", "exercises[1].exerciseId", "exercises[1].sets[0]")));
    }

    @Test
    void 単位がない値なら400() throws Exception {
      Map<String, Object> data = valid();
      data.put("weightUnit", "g");
      data.put("distanceUnit", "m");

      create(accounts.signup(), data)
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("$.errors[*].field")
                  .value(containsInAnyOrder("weightUnit", "distanceUnit")));
    }

    @Test
    void 記録のJSONのパートがなければ400_multipartでなければ415() throws Exception {
      Account me = accounts.signup();

      mockMvc
          .perform(multipart("/api/workouts").header(HttpHeaders.AUTHORIZATION, me.bearer()))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
      mockMvc
          .perform(
              post("/api/workouts")
                  .header(HttpHeaders.AUTHORIZATION, me.bearer())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(valid())))
          .andExpect(status().isUnsupportedMediaType())
          .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void ログインしていなければ401() throws Exception {
      mockMvc.perform(multipart("/api/workouts")).andExpect(status().isUnauthorized());
    }
  }

  @Nested
  class 詳細_A21 {

    @Test
    void 他人の公開の記録は見られる_自分の記録ではない() throws Exception {
      Account author = accounts.signup();
      long id = fixture.of(author.id()).weightReps("スクワット", 100, 5, 3).save();

      detail(accounts.signup(), id)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(id))
          .andExpect(jsonPath("$.author.username").value(author.username()))
          .andExpect(jsonPath("$.exercises[0].name").value("スクワット"))
          .andExpect(jsonPath("$.exercises[0].sets.length()").value(3))
          .andExpect(jsonPath("$.mine").value(false));
    }

    @Test
    void 他人の自分専用の種目も_名前はそのまま見える() throws Exception {
      Account author = accounts.signup();
      long custom = customExercise(author, "ブルガリアンスクワット");
      Map<String, Object> data = valid();
      data.put("exercises", List.of(exercise(custom, set(20, 10))));
      long id = idOf(create(author, data));

      detail(accounts.signup(), id)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.exercises[0].name").value("ブルガリアンスクワット"));
    }

    @Test
    void 見てはいけない記録とない記録は_同じ404() throws Exception {
      Account author = accounts.signup();
      Account stranger = accounts.signup();
      long privateOne = fixture.of(author.id()).visibility("private").save();
      long followersOnly = fixture.of(author.id()).visibility("followers").save();
      Account locked = accounts.signup();
      accounts.makePrivate(locked);
      long lockedPublic = fixture.of(locked.id()).save();

      for (long id : new long[] {privateOne, followersOnly, lockedPublic, Long.MAX_VALUE}) {
        detail(stranger, id)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("WORKOUT_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("記録が見つかりません"));
      }
      // 本人はどれも見られる
      for (long id : new long[] {privateOne, followersOnly}) {
        detail(author, id).andExpect(status().isOk()).andExpect(jsonPath("$.mine").value(true));
      }
      detail(locked, lockedPublic).andExpect(status().isOk());
    }

    /** 他人が見る応答のキーは、許可リストと完全に同じ（docs/test-plan.md 4.2）。キーを足したら、本人以外に出してよいかを考えてからここに足す */
    @Test
    void 他人が見る応答のキーは許可リストと同じ() throws Exception {
      Account author = accounts.signup();
      long id =
          fixture
              .of(author.id())
              .durationMin(60)
              .weightReps("ベンチプレス", 60, 10, 1)
              .timeDistance("ランニング", 1800, 5.0)
              .save();

      String body = detail(accounts.signup(), id).andReturn().getResponse().getContentAsString();

      assertThat(keyPaths(objectMapper.readTree(body)))
          .containsExactlyInAnyOrder(
              "id",
              "author",
              "author.username",
              "author.displayName",
              "author.iconUrl",
              "author.isPrivate",
              "performedOn",
              "createdAt",
              "editedAt",
              "body",
              "durationMin",
              "visibility",
              "exercises",
              "exercises[].exerciseId",
              "exercises[].name",
              "exercises[].bodyPart",
              "exercises[].recordType",
              "exercises[].isRunning",
              "exercises[].personalBest",
              "exercises[].sets",
              "exercises[].sets[].weightKg",
              "exercises[].sets[].reps",
              "exercises[].sets[].durationSec",
              "exercises[].sets[].distanceKm",
              "exercises[].sets[].personalBest",
              "images",
              "likeCount",
              "likedByMe",
              "commentCount",
              "mine");
    }

    @Test
    void IDが数でなければ400_ログインしていなければ401() throws Exception {
      detail(accounts.signup(), "abc").andExpect(status().isBadRequest());
      mockMvc.perform(get("/api/workouts/1")).andExpect(status().isUnauthorized());
    }
  }

  // ===== 補助 =====

  private ResultActions create(Account account, Map<String, Object> data) throws Exception {
    MockMultipartFile part =
        new MockMultipartFile(
            "data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(data));
    return mockMvc.perform(
        multipart("/api/workouts").file(part).header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }

  private ResultActions detail(Account account, Object id) throws Exception {
    return mockMvc.perform(
        get("/api/workouts/" + id).header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }

  private long idOf(ResultActions created) throws Exception {
    String body =
        created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(body).get("id").asLong();
  }

  /** 正しい記録の JSON（必要な項目だけを変えて使う） */
  private Map<String, Object> valid() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("performedOn", today());
    data.put("body", "");
    data.put("visibility", "public");
    data.put("weightUnit", "kg");
    data.put("distanceUnit", "km");
    data.put("exercises", List.of(exercise(PUSH_UP, reps(20))));
    return data;
  }

  private String today() {
    return JapanTime.today(clock).toString();
  }

  @SafeVarargs
  private static Map<String, Object> exercise(long exerciseId, Map<String, Object>... sets) {
    return Map.of("exerciseId", exerciseId, "sets", List.of(sets));
  }

  private static Map<String, Object> set(double weight, int reps) {
    return Map.of("weight", weight, "reps", reps);
  }

  private static Map<String, Object> reps(int reps) {
    return Map.of("reps", reps);
  }

  private long customExercise(Account owner, String name) {
    return exerciseRepository
        .insertCustom(
            new NewExercise(
                owner.id(),
                name,
                ExerciseRules.nameKey(name),
                "legs",
                "weight_reps",
                new BigDecimal("3.5"),
                OffsetDateTime.now(clock)))
        .getId();
  }

  /** JSON のキーの道のり（配列の中は {@code []}）を、重複なしで集める */
  private static TreeSet<String> keyPaths(JsonNode node) {
    TreeSet<String> paths = new TreeSet<>();
    collect(node, "", paths);
    return paths;
  }

  private static void collect(JsonNode node, String prefix, TreeSet<String> paths) {
    if (node.isObject()) {
      for (Map.Entry<String, JsonNode> field : node.properties()) {
        String path = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
        paths.add(path);
        collect(field.getValue(), path, paths);
      }
    } else if (node.isArray()) {
      for (JsonNode element : node) {
        collect(element, prefix + "[]", paths);
      }
    }
  }
}
