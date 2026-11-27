package com.okimoto.fitlog.stats;

import static com.okimoto.fitlog.jooq.Tables.EXERCISES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.okimoto.fitlog.exercise.repository.ExerciseRepository;
import com.okimoto.fitlog.exercise.repository.NewExercise;
import com.okimoto.fitlog.support.MutableClock;
import com.okimoto.fitlog.support.SqlCounter;
import com.okimoto.fitlog.support.TestAccounts;
import com.okimoto.fitlog.support.TestAccounts.Account;
import com.okimoto.fitlog.support.TestcontainersConfiguration;
import com.okimoto.fitlog.support.WorkoutFixture;
import com.okimoto.fitlog.workout.calc.StoredSet;
import com.okimoto.fitlog.workout.repository.WorkoutRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/**
 * 週間の集計（A-90）・月間の集計（A-91）を、API から DB まで通して確かめる（docs/api.md「集計」、docs/test-plan.md 4.2・4.4）。
 *
 * <p>期間を指定しないときの「今週」「今月」の境界を確かめるため、時計を差し替える（ほかの結合テストとは別の Spring の文脈で動く）。アクセストークンもこの時計で作るので、
 * 時計を合わせてから登録する
 */
@Import({TestcontainersConfiguration.class, StatsIntegrationTest.Clocks.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StatsIntegrationTest {

  /** 日本時間 2026-11-11（水）12:00 */
  private static final Instant WEDNESDAY = Instant.parse("2026-11-11T03:00:00Z");

  private static final MutableClock CLOCK = new MutableClock(WEDNESDAY);

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DSLContext dsl;
  @Autowired private WorkoutRepository workoutRepository;
  @Autowired private SqlCounter sqlCounter;
  @Autowired private ExerciseRepository exerciseRepository;

  private TestAccounts accounts;
  private WorkoutFixture workouts;

  @TestConfiguration
  static class Clocks {
    @Bean
    @Primary
    MutableClock testClock() {
      return CLOCK;
    }
  }

  @BeforeEach
  void setUp() {
    CLOCK.set(WEDNESDAY);
    accounts = new TestAccounts(mockMvc, objectMapper, dsl);
    workouts = new WorkoutFixture(dsl, workoutRepository);
  }

  @Nested
  class 週間_A90 {

    @Test
    void 集計値と前の週の値_曜日ごと_部位別のセット数() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      save(me, "2026-11-09").weightReps("ベンチプレス", 60, 10, 3).save();
      save(me, "2026-11-11").timeDistance("ランニング", 1800, 5.0).save();
      // 自分のみの記録も入る
      save(me, "2026-11-15").visibility("private").weightReps("スクワット", 100, 5, 1).save();
      // 前の週
      save(me, "2026-11-08").weightReps("ベンチプレス", 50, 10, 1).save();
      // 他人の記録は入らない
      save(sato, "2026-11-10").weightReps("デッドリフト", 200, 10, 5).save();

      weekly(me, "date=2026-11-12")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.from").value("2026-11-09"))
          .andExpect(jsonPath("$.to").value("2026-11-15"))
          .andExpect(jsonPath("$.summary.trainingDays").value(3))
          .andExpect(jsonPath("$.summary.totalVolumeKg").value(2300.0))
          .andExpect(jsonPath("$.summary.totalDurationSec").value(1800))
          .andExpect(jsonPath("$.summary.totalDistanceKm").value(5.0))
          // 消費カロリーは段階 5 で作る
          .andExpect(jsonPath("$.summary.caloriesKcal").value(nullValue()))
          .andExpect(jsonPath("$.summary.caloriesExcludedWorkoutCount").value(0))
          .andExpect(jsonPath("$.previous.trainingDays").value(1))
          .andExpect(jsonPath("$.previous.totalVolumeKg").value(500.0))
          .andExpect(jsonPath("$.previous.totalDurationSec").value(0))
          .andExpect(jsonPath("$.daily", hasSize(7)))
          .andExpect(
              jsonPath("$.daily[*].date")
                  .value(
                      contains(
                          "2026-11-09",
                          "2026-11-10",
                          "2026-11-11",
                          "2026-11-12",
                          "2026-11-13",
                          "2026-11-14",
                          "2026-11-15")))
          .andExpect(
              jsonPath("$.daily[*].volumeKg")
                  .value(contains(1800.0, 0.0, 0.0, 0.0, 0.0, 0.0, 500.0)))
          .andExpect(jsonPath("$.daily[*].durationSec").value(contains(0, 0, 1800, 0, 0, 0, 0)))
          .andExpect(jsonPath("$.bodyParts[*].bodyPart").value(contains("chest", "legs", "cardio")))
          .andExpect(jsonPath("$.bodyParts[*].setCount").value(contains(3, 1, 1)));
    }

    @Test
    void 期間がなければ今週_日曜の終わりまでは前の週で_月曜の0時から次の週() throws Exception {
      // 日本時間 2026-11-15（日）23:59:59
      CLOCK.set(Instant.parse("2026-11-15T14:59:59Z"));
      Account me = accounts.signup();
      weekly(me, "").andExpect(jsonPath("$.from").value("2026-11-09"));

      // 日本時間 2026-11-16（月）00:00:00
      CLOCK.set(Instant.parse("2026-11-15T15:00:00Z"));
      weekly(me, "")
          .andExpect(jsonPath("$.from").value("2026-11-16"))
          .andExpect(jsonPath("$.to").value("2026-11-22"));
    }

    @Test
    void 未来の期間も_エラーにせず0を返す() throws Exception {
      Account me = accounts.signup();
      save(me, "2026-11-11").weightReps("ベンチプレス", 60, 10, 3).save();

      weekly(me, "date=2027-01-06")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.from").value("2027-01-04"))
          .andExpect(jsonPath("$.summary.trainingDays").value(0))
          .andExpect(jsonPath("$.summary.totalVolumeKg").value(0.0))
          .andExpect(jsonPath("$.daily", hasSize(7)))
          .andExpect(jsonPath("$.daily[*].volumeKg", everyItem(is(0.0))))
          .andExpect(jsonPath("$.bodyParts", hasSize(0)));
    }

    @Test
    void 日付が正しくなければ400_ログインしていなければ401() throws Exception {
      Account me = accounts.signup();
      for (String query : new String[] {"date=2026-13-01", "date=abc", "date=2026/11/12"}) {
        weekly(me, query)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
      }
      mockMvc.perform(get("/api/stats/weekly")).andExpect(status().isUnauthorized());
    }
  }

  @Nested
  class 月間_A91 {

    @Test
    void 週ごとは月曜から_月をまたぐ週はその月の日だけを数える() throws Exception {
      Account me = accounts.signup();
      // 2026-11-01 は日曜（10-26 からの週）、11-30 は月曜（12-06 までの週）
      save(me, "2026-10-31").weightReps("ベンチプレス", 50, 10, 1).save();
      save(me, "2026-11-01").weightReps("ベンチプレス", 60, 10, 1).save();
      save(me, "2026-11-30").timeDistance("ランニング", 1800, 5.0).save();
      save(me, "2026-12-01").timeDistance("ランニング", 3600, 10.0).save();

      monthly(me, "month=2026-11")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.from").value("2026-11-01"))
          .andExpect(jsonPath("$.to").value("2026-11-30"))
          .andExpect(jsonPath("$.summary.trainingDays").value(2))
          .andExpect(jsonPath("$.summary.totalVolumeKg").value(600.0))
          .andExpect(jsonPath("$.summary.totalDistanceKm").value(5.0))
          .andExpect(jsonPath("$.summary.caloriesKcal").value(nullValue()))
          .andExpect(jsonPath("$.previous.trainingDays").value(1))
          .andExpect(jsonPath("$.previous.totalVolumeKg").value(500.0))
          .andExpect(
              jsonPath("$.weekly[*].weekStart")
                  .value(
                      contains(
                          "2026-10-26",
                          "2026-11-02",
                          "2026-11-09",
                          "2026-11-16",
                          "2026-11-23",
                          "2026-11-30")))
          .andExpect(jsonPath("$.weekly[0].trainingDays").value(1))
          .andExpect(jsonPath("$.weekly[0].volumeKg").value(600.0))
          .andExpect(jsonPath("$.weekly[5].trainingDays").value(1))
          .andExpect(jsonPath("$.weekly[5].durationSec").value(1800))
          .andExpect(jsonPath("$.weekly[5].distanceKm").value(5.0))
          .andExpect(jsonPath("$.bodyParts[*].bodyPart").value(contains("chest", "cardio")))
          .andExpect(jsonPath("$.daily").doesNotExist());
    }

    @Test
    void 期間がなければ今月_月末の終わりまでは今の月で_1日の0時から次の月() throws Exception {
      // 日本時間 2026-11-30 23:59:59
      CLOCK.set(Instant.parse("2026-11-30T14:59:59Z"));
      Account me = accounts.signup();
      monthly(me, "").andExpect(jsonPath("$.from").value("2026-11-01"));

      // 日本時間 2026-12-01 00:00:00
      CLOCK.set(Instant.parse("2026-11-30T15:00:00Z"));
      monthly(me, "")
          .andExpect(jsonPath("$.from").value("2026-12-01"))
          .andExpect(jsonPath("$.to").value("2026-12-31"));
    }

    @Test
    void 月が正しくなければ400_ログインしていなければ401() throws Exception {
      Account me = accounts.signup();
      for (String query : new String[] {"month=2026-13", "month=2026-1", "month=2026-11-01"}) {
        monthly(me, query)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
      }
      mockMvc.perform(get("/api/stats/monthly")).andExpect(status().isUnauthorized());
    }
  }

  @Nested
  class カレンダー_A92 {

    @Test
    void その月の記録がある日を返す_自分のみの記録も入り_他人の記録は入らない() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      save(me, "2026-11-30").save();
      save(me, "2026-11-01").visibility("private").save();
      save(me, "2026-11-04").save();
      save(me, "2026-11-04").save();
      save(me, "2026-10-31").save();
      save(me, "2026-12-01").save();
      save(sato, "2026-11-06").save();

      calendar(me, "month=2026-11")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.month").value("2026-11"))
          .andExpect(jsonPath("$.dates").value(contains("2026-11-01", "2026-11-04", "2026-11-30")));
    }

    @Test
    void 月がなければ今月_正しくなければ400_ログインしていなければ401() throws Exception {
      // 日本時間 2026-12-01 00:00:00（UTC ではまだ 11-30）
      CLOCK.set(Instant.parse("2026-11-30T15:00:00Z"));
      Account me = accounts.signup();
      calendar(me, "").andExpect(jsonPath("$.month").value("2026-12"));
      calendar(me, "month=2026-13")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
      mockMvc.perform(get("/api/stats/calendar")).andExpect(status().isUnauthorized());
    }
  }

  @Nested
  class その日の記録_A93 {

    @Test
    void その日の自分の記録を投稿日時の順に_投稿カードの形で返す() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      save(me, "2026-11-09").weightReps("ベンチプレス", 60, 10, 1).save();
      long later =
          save(me, "2026-11-10")
              .createdAt(OffsetDateTime.parse("2026-11-10T20:00:00+09:00"))
              .weightReps("ベンチプレス", 70, 5, 1)
              .save();
      long first =
          save(me, "2026-11-10")
              .createdAt(OffsetDateTime.parse("2026-11-10T07:00:00+09:00"))
              .visibility("private")
              .reps("腕立て伏せ", 20, 1)
              .save();
      save(sato, "2026-11-10").save();

      day(me, "date=2026-11-10")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(2)))
          .andExpect(jsonPath("$[*].id").value(contains((int) first, (int) later)))
          .andExpect(jsonPath("$[0].visibility").value("private"))
          .andExpect(jsonPath("$[0].mine").value(true))
          .andExpect(jsonPath("$[1].exerciseSummaries[0].name").value("ベンチプレス"))
          .andExpect(jsonPath("$[1].exerciseSummaries[0].personalBest").value(true));
    }

    @Test
    void 日付がなければ今日_正しくなければ400_ログインしていなければ401() throws Exception {
      // 日本時間 2026-11-11 00:00:00（UTC ではまだ 11-10）
      CLOCK.set(Instant.parse("2026-11-10T15:00:00Z"));
      Account me = accounts.signup();
      long today = save(me, "2026-11-11").save();
      save(me, "2026-11-10").save();

      day(me, "").andExpect(jsonPath("$[*].id").value(contains((int) today)));
      day(me, "date=2026-11-31")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
      mockMvc.perform(get("/api/stats/calendar/workouts")).andExpect(status().isUnauthorized());
    }

    /** 1件のときと5件のときで、SQL の数が同じ（docs/test-plan.md 4.7） */
    @Test
    void 記録の数によらずSQLの数が同じ() throws Exception {
      Account me = accounts.signup();
      saveRich(me);
      int one =
          sqlCounter.count(() -> day(me, "date=2026-11-10").andExpect(jsonPath("$", hasSize(1))));

      for (int i = 0; i < 4; i++) {
        saveRich(me);
      }

      assertThat(one).isPositive();
      assertThat(
              sqlCounter.count(
                  () -> day(me, "date=2026-11-10").andExpect(jsonPath("$", hasSize(5)))))
          .isEqualTo(one);
    }

    private void saveRich(Account account) {
      long id =
          save(account, "2026-11-10")
              .weightReps("ベンチプレス", 60, 10, 3)
              .reps("腕立て伏せ", 20, 3)
              .timeDistance("ランニング", 1800, 5.0)
              .save();
      workoutRepository.insertImages(id, List.of("workouts/day.jpg"), OffsetDateTime.now(CLOCK));
    }
  }

  @Nested
  class ランニング_A94 {

    @Test
    void 月間の集計値と前の月の値_最速ペースと最長距離の記録() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      long fast = save(me, "2026-11-03").timeDistance("ランニング", 1500, 5.0).save();
      long longest = save(me, "2026-11-08").timeDistance("ジョギング", 4200, 10.0).save();
      // 距離のないセットは時間・回数に入る。ランニングでない種目は入らない
      save(me, "2026-11-10")
          .exercise("トレッドミル", List.of(new StoredSet(null, null, 600, null)))
          .timeDistance("サイクリング", 3600, 30.0)
          .save();
      save(me, "2026-10-31").timeDistance("ランニング", 1800, 5.0).save();
      save(sato, "2026-11-04").timeDistance("ランニング", 1000, 5.0).save();

      running(me, "period=month&date=2026-11-12")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.from").value("2026-11-01"))
          .andExpect(jsonPath("$.to").value("2026-11-30"))
          .andExpect(jsonPath("$.summary.distanceKm").value(15.0))
          .andExpect(jsonPath("$.summary.durationSec").value(6300))
          .andExpect(jsonPath("$.summary.runCount").value(3))
          // (1500 + 4200) ÷ 15 = 380
          .andExpect(jsonPath("$.summary.avgPaceSecPerKm").value(380))
          .andExpect(jsonPath("$.previous.distanceKm").value(5.0))
          .andExpect(jsonPath("$.previous.runCount").value(1))
          .andExpect(jsonPath("$.previous.avgPaceSecPerKm").value(360))
          .andExpect(jsonPath("$.bestPace.workoutId").value(fast))
          .andExpect(jsonPath("$.bestPace.performedOn").value("2026-11-03"))
          .andExpect(jsonPath("$.bestPace.distanceKm").value(5.0))
          .andExpect(jsonPath("$.bestPace.durationSec").value(1500))
          .andExpect(jsonPath("$.bestPace.paceSecPerKm").value(300))
          .andExpect(jsonPath("$.longestRun.workoutId").value(longest))
          .andExpect(jsonPath("$.longestRun.paceSecPerKm").value(420))
          .andExpect(jsonPath("$.monthly", hasSize(0)));
    }

    @Test
    void 年間は1月から12月の月ごとの走行距離も返す() throws Exception {
      Account me = accounts.signup();
      save(me, "2026-01-01").timeDistance("ランニング", 1800, 5.0).save();
      save(me, "2026-11-11").timeDistance("ランニング", 1800, 5.0).save();
      save(me, "2025-12-31").timeDistance("ランニング", 600, 2.0).save();

      running(me, "period=year&date=2026-06-15")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.from").value("2026-01-01"))
          .andExpect(jsonPath("$.to").value("2026-12-31"))
          .andExpect(jsonPath("$.summary.distanceKm").value(10.0))
          .andExpect(jsonPath("$.previous.distanceKm").value(2.0))
          .andExpect(jsonPath("$.monthly", hasSize(12)))
          .andExpect(jsonPath("$.monthly[0].month").value("2026-01"))
          .andExpect(jsonPath("$.monthly[0].distanceKm").value(5.0))
          .andExpect(jsonPath("$.monthly[1].distanceKm").value(0.0))
          .andExpect(jsonPath("$.monthly[10].month").value("2026-11"))
          .andExpect(jsonPath("$.monthly[10].distanceKm").value(5.0));
    }

    @Test
    void 記録がなければ0で_ペースと最速ペースと最長距離はnull() throws Exception {
      Account me = accounts.signup();

      running(me, "period=week")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.from").value("2026-11-09"))
          .andExpect(jsonPath("$.summary.distanceKm").value(0.0))
          .andExpect(jsonPath("$.summary.runCount").value(0))
          .andExpect(jsonPath("$.summary.avgPaceSecPerKm").value(nullValue()))
          .andExpect(jsonPath("$.bestPace").value(nullValue()))
          .andExpect(jsonPath("$.longestRun").value(nullValue()));
    }

    @Test
    void 期間の単位がない_正しくない_日付が正しくなければ400_ログインしていなければ401() throws Exception {
      Account me = accounts.signup();
      for (String query :
          new String[] {"", "period=day", "period=WEEK", "period=week&date=2026-13-01"}) {
        running(me, query).andExpect(status().isBadRequest());
      }
      running(me, "period=day").andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
      mockMvc.perform(get("/api/stats/running?period=week")).andExpect(status().isUnauthorized());
    }
  }

  @Nested
  class 記録した種目_A95 {

    @Test
    void 自分が記録した種目を_最後に記録した日つきで返す() throws Exception {
      Account me = accounts.signup();
      save(me, "2026-10-01").weightReps("ベンチプレス", 60, 10, 1).save();
      save(me, "2026-11-10").weightReps("ベンチプレス", 60, 10, 1).reps("懸垂", 10, 1).save();

      exercises(me)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(2)))
          .andExpect(jsonPath("$[0].exercise.name").value("ベンチプレス"))
          .andExpect(jsonPath("$[0].exercise.recordType").value("weight_reps"))
          .andExpect(jsonPath("$[0].exercise.custom").value(false))
          .andExpect(jsonPath("$[0].lastPerformedOn").value("2026-11-10"))
          .andExpect(jsonPath("$[1].exercise.name").value("懸垂"));
      mockMvc.perform(get("/api/stats/exercises")).andExpect(status().isUnauthorized());
    }

    /** 1種目のときと5種目のときで、SQL の数が同じ（docs/test-plan.md 4.7） */
    @Test
    void 種目の数によらずSQLの数が同じ() throws Exception {
      Account me = accounts.signup();
      save(me, "2026-11-10").weightReps("ベンチプレス", 60, 10, 1).save();
      int one = sqlCounter.count(() -> exercises(me).andExpect(jsonPath("$", hasSize(1))));

      save(me, "2026-11-10")
          .weightReps("スクワット", 100, 5, 1)
          .weightReps("デッドリフト", 120, 5, 1)
          .reps("懸垂", 10, 1)
          .timeDistance("ランニング", 1800, 5.0)
          .save();

      assertThat(one).isPositive();
      assertThat(sqlCounter.count(() -> exercises(me).andExpect(jsonPath("$", hasSize(5)))))
          .isEqualTo(one);
    }
  }

  @Nested
  class 種目ごとの推移_A96 {

    @Test
    void 期間がなければ直近3か月の点と_全期間の自己ベストと推定1RMの最高() throws Exception {
      Account me = accounts.signup();
      long bench = exerciseId("ベンチプレス");
      // 2026-11-11 の3か月前は 2026-08-11。その日は入り、前の日は入らない
      long best = save(me, "2026-08-10").weightReps("ベンチプレス", 120, 1, 1).save();
      long first = save(me, "2026-08-11").weightReps("ベンチプレス", 100, 10, 1).save();
      long last = save(me, "2026-11-10").weightReps("ベンチプレス", 100, 5, 2).save();
      // 他人の記録は入らない
      save(accounts.signup(), "2026-11-10").weightReps("ベンチプレス", 200, 1, 1).save();

      trend(me, bench, "")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.exercise.id").value(bench))
          .andExpect(jsonPath("$.exercise.name").value("ベンチプレス"))
          .andExpect(jsonPath("$.personalBest.weightKg").value(120.0))
          .andExpect(jsonPath("$.personalBest.performedOn").value("2026-08-10"))
          .andExpect(jsonPath("$.personalBest.workoutId").value(best))
          // 100 × 10 ＝ 133.333 が 120 × 1 より大きい
          .andExpect(jsonPath("$.bestE1rm.e1rmKg").value(133.333))
          .andExpect(jsonPath("$.bestE1rm.performedOn").value("2026-08-11"))
          .andExpect(jsonPath("$.bestE1rm.workoutId").value(first))
          .andExpect(jsonPath("$.points", hasSize(2)))
          .andExpect(jsonPath("$.points[0].date").value("2026-08-11"))
          .andExpect(jsonPath("$.points[1].date").value("2026-11-10"))
          .andExpect(jsonPath("$.points[1].workoutId").value(last))
          .andExpect(jsonPath("$.points[1].maxWeightKg").value(100.0))
          .andExpect(jsonPath("$.points[1].maxE1rmKg").value(116.667))
          .andExpect(jsonPath("$.points[1].maxWeightReps").value(5))
          // 記録方式で使わない値は null
          .andExpect(jsonPath("$.points[1].totalReps").value(nullValue()))
          .andExpect(jsonPath("$.points[1].totalDurationSec").value(nullValue()))
          .andExpect(jsonPath("$.points[1].paceSecPerKm").value(nullValue()));

      trend(me, bench, "range=all").andExpect(jsonPath("$.points", hasSize(3)));
      trend(me, bench, "range=1y").andExpect(jsonPath("$.points", hasSize(3)));
      trend(me, bench, "range=3m").andExpect(jsonPath("$.points", hasSize(2)));
    }

    @Test
    void ランニングは合計時間と合計距離とペース_重量の種目でなければ自己ベストはnull() throws Exception {
      Account me = accounts.signup();
      save(me, "2026-11-10").timeDistance("ランニング", 1800, 5.0).save();

      trend(me, exerciseId("ランニング"), "")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.personalBest").value(nullValue()))
          .andExpect(jsonPath("$.bestE1rm").value(nullValue()))
          .andExpect(jsonPath("$.points[0].totalDurationSec").value(1800))
          .andExpect(jsonPath("$.points[0].totalDistanceKm").value(5.0))
          .andExpect(jsonPath("$.points[0].paceSecPerKm").value(360))
          .andExpect(jsonPath("$.points[0].maxWeightKg").value(nullValue()));
    }

    @Test
    void 記録していない種目と自分の種目は200_他人の種目と存在しない種目は404() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      long mine = custom(me, "マイプレス");
      long satos = custom(sato, "佐藤プレス");

      trend(me, exerciseId("スクワット"), "")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.points", hasSize(0)))
          .andExpect(jsonPath("$.personalBest").value(nullValue()));
      trend(me, mine, "")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.exercise.custom").value(true));
      for (long id : new long[] {satos, Long.MAX_VALUE}) {
        trend(me, id, "")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("EXERCISE_NOT_FOUND"));
      }
    }

    @Test
    void 期間が正しくない_種目のIDが数でなければ400_ログインしていなければ401() throws Exception {
      Account me = accounts.signup();
      long bench = exerciseId("ベンチプレス");
      for (String query : new String[] {"range=6m", "range=ALL"}) {
        trend(me, bench, query)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
      }
      mockMvc
          .perform(get("/api/stats/exercises/abc").header(HttpHeaders.AUTHORIZATION, me.bearer()))
          .andExpect(status().isBadRequest());
      mockMvc.perform(get("/api/stats/exercises/" + bench)).andExpect(status().isUnauthorized());
    }
  }

  private ResultActions exercises(Account account) throws Exception {
    return mockMvc.perform(
        get("/api/stats/exercises").header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }

  private ResultActions trend(Account account, long exerciseId, String query) throws Exception {
    return mockMvc.perform(
        get("/api/stats/exercises/" + exerciseId + "?" + query)
            .header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }

  private long exerciseId(String name) {
    return dsl.select(EXERCISES.ID)
        .from(EXERCISES)
        .where(EXERCISES.NAME.eq(name))
        .and(EXERCISES.OWNER_ID.isNull())
        .fetchSingle()
        .value1();
  }

  private long custom(Account account, String name) {
    return exerciseRepository
        .insertCustom(
            new NewExercise(
                account.id(),
                name,
                name,
                "chest",
                "weight_reps",
                new BigDecimal("6.0"),
                OffsetDateTime.now(CLOCK)))
        .getId();
  }

  private WorkoutFixture.Builder save(Account account, String performedOn) {
    return workouts.of(account.id()).performedOn(LocalDate.parse(performedOn));
  }

  private ResultActions weekly(Account account, String query) throws Exception {
    return mockMvc.perform(
        get("/api/stats/weekly?" + query).header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }

  private ResultActions running(Account account, String query) throws Exception {
    return mockMvc.perform(
        get("/api/stats/running?" + query).header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }

  private ResultActions calendar(Account account, String query) throws Exception {
    return mockMvc.perform(
        get("/api/stats/calendar?" + query).header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }

  private ResultActions day(Account account, String query) throws Exception {
    return mockMvc.perform(
        get("/api/stats/calendar/workouts?" + query)
            .header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }

  private ResultActions monthly(Account account, String query) throws Exception {
    return mockMvc.perform(
        get("/api/stats/monthly?" + query).header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }
}
