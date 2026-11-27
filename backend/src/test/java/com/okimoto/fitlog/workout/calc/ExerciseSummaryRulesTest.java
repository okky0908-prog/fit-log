package com.okimoto.fitlog.workout.calc;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 一覧の種目の要約で、記録方式ごとにどの値を返すか（docs/test-plan.md 5.4 のデシジョンテーブル）。
 *
 * <p>最大のセットの選び方・合計は SQL で集計するので、WorkoutCardRepositoryTest で確かめる。ここでは集計した値から、返す項目を決める
 */
class ExerciseSummaryRulesTest {

  private static final long BENCH_PRESS = 1L;

  @Nested
  class 重量と回数 {

    @Test
    void 全セットが同じなら_その値とセット数() {
      ExerciseSummary summary =
          ExerciseSummaryRules.summarize(
              new ExerciseAggregate(
                  BENCH_PRESS, "ベンチプレス", "weight_reps", 3, true, kg("60.000"), 10, 30, null, null),
              false);

      assertThat(summary)
          .isEqualTo(
              new ExerciseSummary(
                  BENCH_PRESS,
                  "ベンチプレス",
                  "weight_reps",
                  false,
                  3,
                  true,
                  kg("60.000"),
                  10,
                  null,
                  null,
                  null,
                  null));
    }

    @Test
    void 値が違えば_最大のセットの値() {
      ExerciseSummary summary =
          ExerciseSummaryRules.summarize(
              new ExerciseAggregate(
                  BENCH_PRESS, "ベンチプレス", "weight_reps", 3, false, kg("100.000"), 5, 23, null, null),
              false);

      assertThat(summary.uniform()).isFalse();
      assertThat(summary.weightKg()).isEqualTo(kg("100.000"));
      assertThat(summary.reps()).isEqualTo(5);
      assertThat(summary.totalReps()).isNull();
    }
  }

  @Test
  void 自己ベスト更新の印は_渡したとおりに返す() {
    ExerciseSummary summary =
        ExerciseSummaryRules.summarize(
            new ExerciseAggregate(
                BENCH_PRESS, "ベンチプレス", "weight_reps", 1, true, kg("70.000"), 5, 5, null, null),
            true);

    assertThat(summary.personalBest()).isTrue();
  }

  @Nested
  class 回数のみ {

    @Test
    void 全セットが同じなら_その回数() {
      ExerciseSummary summary =
          ExerciseSummaryRules.summarize(
              new ExerciseAggregate(8L, "腕立て伏せ", "reps", 3, true, null, 20, 60, null, null), false);

      assertThat(summary.uniform()).isTrue();
      assertThat(summary.reps()).isEqualTo(20);
      assertThat(summary.totalReps()).isNull();
      assertThat(summary.weightKg()).isNull();
    }

    @Test
    void 値が違えば_回数の合計() {
      ExerciseSummary summary =
          ExerciseSummaryRules.summarize(
              new ExerciseAggregate(8L, "腕立て伏せ", "reps", 3, false, null, 20, 55, null, null),
              false);

      assertThat(summary.uniform()).isFalse();
      assertThat(summary.totalReps()).isEqualTo(55);
      assertThat(summary.reps()).isNull();
    }
  }

  @Nested
  class 時間と距離 {

    @Test
    void 一回なら_時間と距離とペース() {
      ExerciseSummary summary = summarize(1, 1800, km("5.000"));

      assertThat(summary)
          .isEqualTo(
              new ExerciseSummary(
                  48L,
                  "ランニング",
                  "time_distance",
                  false,
                  1,
                  null,
                  null,
                  null,
                  null,
                  1800,
                  km("5.000"),
                  360));
    }

    @Test
    void ペースは1km_あたりの秒数を四捨五入する() {
      // 1000 ÷ 3 = 333.33… → 333、1001 ÷ 2 = 500.5 → 501
      assertThat(summarize(1, 1000, km("3.000")).paceSecPerKm()).isEqualTo(333);
      assertThat(summarize(1, 1001, km("2.000")).paceSecPerKm()).isEqualTo(501);
    }

    @Test
    void 距離がなければ_距離もペースもnull() {
      ExerciseSummary summary = summarize(1, 1800, null);

      assertThat(summary.durationSec()).isEqualTo(1800);
      assertThat(summary.distanceKm()).isNull();
      assertThat(summary.paceSecPerKm()).isNull();
    }

    @Test
    void 二回以上なら_合計でペースはnull() {
      ExerciseSummary summary = summarize(2, 3000, km("8.000"));

      assertThat(summary.setCount()).isEqualTo(2);
      assertThat(summary.durationSec()).isEqualTo(3000);
      assertThat(summary.distanceKm()).isEqualTo(km("8.000"));
      assertThat(summary.paceSecPerKm()).isNull();
      assertThat(summary.uniform()).isNull();
    }

    private ExerciseSummary summarize(int setCount, int durationSec, BigDecimal distanceKm) {
      return ExerciseSummaryRules.summarize(
          new ExerciseAggregate(
              48L,
              "ランニング",
              "time_distance",
              setCount,
              setCount == 1,
              null,
              null,
              null,
              durationSec,
              distanceKm),
          false);
    }
  }

  private static BigDecimal kg(String value) {
    return new BigDecimal(value);
  }

  private static BigDecimal km(String value) {
    return new BigDecimal(value);
  }
}
