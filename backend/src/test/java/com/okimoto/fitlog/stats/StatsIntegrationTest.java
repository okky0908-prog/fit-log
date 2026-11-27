package com.okimoto.fitlog.stats;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.okimoto.fitlog.support.MutableClock;
import com.okimoto.fitlog.support.TestAccounts;
import com.okimoto.fitlog.support.TestAccounts.Account;
import com.okimoto.fitlog.support.TestcontainersConfiguration;
import com.okimoto.fitlog.support.WorkoutFixture;
import com.okimoto.fitlog.workout.repository.WorkoutRepository;
import java.time.Instant;
import java.time.LocalDate;
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

  private WorkoutFixture.Builder save(Account account, String performedOn) {
    return workouts.of(account.id()).performedOn(LocalDate.parse(performedOn));
  }

  private ResultActions weekly(Account account, String query) throws Exception {
    return mockMvc.perform(
        get("/api/stats/weekly?" + query).header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }

  private ResultActions monthly(Account account, String query) throws Exception {
    return mockMvc.perform(
        get("/api/stats/monthly?" + query).header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }
}
