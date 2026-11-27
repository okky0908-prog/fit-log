package com.okimoto.fitlog.stats.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.okimoto.fitlog.stats.calc.RunningRules.MonthDistance;
import com.okimoto.fitlog.stats.calc.RunningTotals;
import com.okimoto.fitlog.stats.calc.StatsRules.Period;
import com.okimoto.fitlog.support.TestcontainersConfiguration;
import com.okimoto.fitlog.support.WorkoutFixture;
import com.okimoto.fitlog.user.repository.NewUser;
import com.okimoto.fitlog.user.repository.UserRepository;
import com.okimoto.fitlog.workout.calc.StoredSet;
import com.okimoto.fitlog.workout.repository.WorkoutRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * ランニングの集計の SQL（docs/test-plan.md 5.11
 * の最速ペース・最長距離）。ランニングの種目（is_running）のセットだけを、本人の記録から、公開範囲に関係なく集計する。
 *
 * <p>テストごとに新しいユーザーを作り、ロールバックする
 */
@JooqTest
@Import({
  TestcontainersConfiguration.class,
  RunningRepository.class,
  WorkoutRepository.class,
  UserRepository.class
})
@ActiveProfiles("test")
class RunningRepositoryTest {

  /** 2026-11 */
  private static final Period NOVEMBER =
      new Period(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"));

  @Autowired private RunningRepository repository;
  @Autowired private WorkoutRepository workoutRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private DSLContext dsl;

  private WorkoutFixture workouts;
  private long me;

  @BeforeEach
  void setUp() {
    workouts = new WorkoutFixture(dsl, workoutRepository);
    me = user("run_me");
  }

  @Nested
  class 合計 {

    @Test
    void ランニングの種目のセットだけを_距離と時間と回数で合計する() {
      workouts
          .of(me)
          .performedOn(day("2026-11-03"))
          .timeDistance("ランニング", 1800, 5.0)
          .timeDistance("ジョギング", 1500, 4.0)
          // 距離のないセットは時間・回数に入り、平均ペースの時間には入らない
          .exercise("トレッドミル", List.of(run(600, null)))
          // ランニングでない有酸素・筋トレは入らない
          .timeDistance("サイクリング", 3600, 20.0)
          .weightReps("ベンチプレス", 60, 10, 3)
          .save();
      // 自分のみの記録も入る
      workouts
          .of(me)
          .performedOn(day("2026-11-30"))
          .visibility("private")
          .timeDistance("ランニング", 600, 2.0)
          .save();

      assertThat(repository.totals(me, NOVEMBER))
          .isEqualTo(new RunningTotals(km("11.000"), 4500, 4, 3900));
    }

    @Test
    void 期間の外と他人の記録は入らない() {
      long sato = user("run_sato");
      workouts.of(me).performedOn(day("2026-10-31")).timeDistance("ランニング", 1800, 5.0).save();
      workouts.of(me).performedOn(day("2026-12-01")).timeDistance("ランニング", 1800, 5.0).save();
      workouts.of(sato).performedOn(day("2026-11-10")).timeDistance("ランニング", 1800, 5.0).save();
      workouts.of(me).performedOn(day("2026-11-01")).timeDistance("ランニング", 300, 1.0).save();

      assertThat(repository.totals(me, NOVEMBER))
          .isEqualTo(new RunningTotals(km("1.000"), 300, 1, 300));
    }

    @Test
    void 記録がなければ0() {
      assertThat(repository.totals(me, NOVEMBER))
          .isEqualTo(new RunningTotals(km("0.000"), 0, 0, 0));
    }
  }

  @Nested
  class 最速ペース {

    @Test
    void 距離が1km以上のセットのうち_ペースが一番速い1回() {
      // 0.99km は 1km 未満なので、ペースが一番速くても（約152秒/km）対象にしない
      saveRun("2026-11-02", 150, 0.99);
      long fast = saveRun("2026-11-03", 1500, 5.0);
      saveRun("2026-11-04", 1800, 5.0);

      assertThat(repository.bestPace(me, NOVEMBER))
          .contains(new RunRecord(fast, day("2026-11-03"), km("5.000"), 1500));
    }

    @Test
    void 距離1kmちょうどのセットは対象にする() {
      saveRun("2026-11-03", 1500, 5.0);
      // 1.00km 280秒（280秒/km）は 5km（300秒/km）より速い
      long justOneKm = saveRun("2026-11-05", 280, 1.0);

      assertThat(repository.bestPace(me, NOVEMBER).orElseThrow().workoutId()).isEqualTo(justOneKm);
    }

    @Test
    void ペースが同じなら距離が長い方_距離も同じなら実施日が新しい方() {
      saveRun("2026-11-02", 1500, 5.0);
      long longer = saveRun("2026-11-03", 3000, 10.0);
      assertThat(repository.bestPace(me, NOVEMBER).orElseThrow().workoutId()).isEqualTo(longer);

      long newer = saveRun("2026-11-04", 3000, 10.0);
      assertThat(repository.bestPace(me, NOVEMBER).orElseThrow().workoutId()).isEqualTo(newer);
    }

    @Test
    void 距離1km以上のセットがなければ空() {
      saveRun("2026-11-02", 150, 0.99);
      workouts
          .of(me)
          .performedOn(day("2026-11-03"))
          .exercise("ランニング", List.of(run(600, null)))
          .save();

      assertThat(repository.bestPace(me, NOVEMBER)).isEmpty();
    }
  }

  @Nested
  class 最長距離 {

    @Test
    void 距離が一番長い1回_同じなら時間が短い方_それも同じなら実施日が新しい方() {
      saveRun("2026-11-02", 3600, 10.0);
      long faster = saveRun("2026-11-03", 3300, 10.0);
      saveRun("2026-11-04", 1500, 5.0);
      assertThat(repository.longestRun(me, NOVEMBER))
          .contains(new RunRecord(faster, day("2026-11-03"), km("10.000"), 3300));

      long newer = saveRun("2026-11-05", 3300, 10.0);
      assertThat(repository.longestRun(me, NOVEMBER).orElseThrow().workoutId()).isEqualTo(newer);
    }

    @Test
    void 距離のあるセットがなければ空_ランニングでない種目は見ない() {
      workouts
          .of(me)
          .performedOn(day("2026-11-03"))
          .exercise("ランニング", List.of(run(600, null)))
          .save();
      workouts.of(me).performedOn(day("2026-11-03")).timeDistance("サイクリング", 3600, 30.0).save();

      assertThat(repository.longestRun(me, NOVEMBER)).isEmpty();
    }
  }

  @Test
  void 月ごとの走行距離_記録のある月だけ() {
    Period year = new Period(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"));
    saveRun("2025-12-31", 600, 2.0);
    saveRun("2026-01-01", 1800, 5.0);
    saveRun("2026-01-31", 1800, 5.5);
    saveRun("2026-12-31", 600, 2.0);

    assertThat(repository.monthlyDistances(me, year))
        .containsExactly(
            new MonthDistance(YearMonth.of(2026, 1), km("10.500")),
            new MonthDistance(YearMonth.of(2026, 12), km("2.000")));
  }

  private long saveRun(String date, int durationSec, double distanceKm) {
    return workouts
        .of(me)
        .performedOn(day(date))
        .timeDistance("ランニング", durationSec, distanceKm)
        .save();
  }

  private static StoredSet run(int durationSec, String distanceKm) {
    return new StoredSet(null, null, durationSec, distanceKm == null ? null : km(distanceKm));
  }

  private static LocalDate day(String value) {
    return LocalDate.parse(value);
  }

  private static BigDecimal km(String value) {
    return new BigDecimal(value);
  }

  private long user(String username) {
    return userRepository
        .insert(
            new NewUser(
                username,
                "表示名",
                username + "@example.com",
                "hash",
                OffsetDateTime.parse("2026-01-01T00:00:00+09:00")))
        .getId();
  }
}
