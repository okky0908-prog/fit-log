package com.okimoto.fitlog.stats.calc;

import static org.assertj.core.api.Assertions.assertThat;

import com.okimoto.fitlog.stats.calc.TrendRules.Point;
import com.okimoto.fitlog.stats.calc.TrendRules.Range;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 種目ごとの推移の期間と、記録方式ごとの点の値（docs/features.md F-84、docs/api.md A-96） */
class TrendRulesTest {

  private static final LocalDate DAY = LocalDate.parse("2026-11-12");

  /** SQL が返す、その日の値（記録方式に関係なく、すべての列が入っている） */
  private static final DayAggregate ALL_VALUES =
      new DayAggregate(
          DAY,
          7L,
          new BigDecimal("100.000"),
          new BigDecimal("116.667"),
          5,
          30,
          10,
          3000,
          new BigDecimal("10.000"),
          2400);

  @Nested
  class 期間 {

    @Test
    void 三か月_一年_全期間の文字だけを受け付ける() {
      assertThat(TrendRules.rangeOf("3m")).contains(Range.THREE_MONTHS);
      assertThat(TrendRules.rangeOf("1y")).contains(Range.ONE_YEAR);
      assertThat(TrendRules.rangeOf("all")).contains(Range.ALL);
      assertThat(TrendRules.rangeOf("3M")).isEmpty();
      assertThat(TrendRules.rangeOf("6m")).isEmpty();
      assertThat(TrendRules.rangeOf("")).isEmpty();
    }

    @Test
    void 始まりの日は今日の3か月前_1年前の同じ日_全期間はなし() {
      assertThat(TrendRules.fromOf(Range.THREE_MONTHS, DAY))
          .contains(LocalDate.parse("2026-08-12"));
      assertThat(TrendRules.fromOf(Range.ONE_YEAR, DAY)).contains(LocalDate.parse("2025-11-12"));
      assertThat(TrendRules.fromOf(Range.ALL, DAY)).isEqualTo(Optional.empty());
    }

    @Test
    void 三か月前に同じ日がなければ_その月の末日() {
      assertThat(TrendRules.fromOf(Range.THREE_MONTHS, LocalDate.parse("2026-05-31")))
          .contains(LocalDate.parse("2026-02-28"));
    }
  }

  @Nested
  class 点 {

    @Test
    void 重量と回数の種目は_最大重量と推定1RMと最大重量の回数だけ() {
      assertThat(TrendRules.point("weight_reps", false, ALL_VALUES))
          .isEqualTo(
              new Point(
                  DAY,
                  7L,
                  new BigDecimal("100.000"),
                  new BigDecimal("116.667"),
                  5,
                  null,
                  null,
                  null,
                  null,
                  null));
    }

    @Test
    void 回数のみの種目は_合計回数と最大回数だけ() {
      assertThat(TrendRules.point("reps", false, ALL_VALUES))
          .isEqualTo(new Point(DAY, 7L, null, null, null, 30, 10, null, null, null));
    }

    @Test
    void ランニングでない時間と距離の種目は_合計時間と合計距離だけでペースは出さない() {
      assertThat(TrendRules.point("time_distance", false, ALL_VALUES))
          .isEqualTo(
              new Point(
                  DAY, 7L, null, null, null, null, null, 3000, new BigDecimal("10.000"), null));
    }

    @Test
    void ランニングは_距離のあるセットの時間の合計を距離の合計で割ったペースも出す() {
      // 2400 秒 ÷ 10km ＝ 240 秒/km（距離のないセットの 600 秒は入れない）
      assertThat(TrendRules.point("time_distance", true, ALL_VALUES).paceSecPerKm()).isEqualTo(240);
    }

    @Test
    void ランニングでも距離のあるセットがなければ_合計距離とペースはnull() {
      DayAggregate noDistance =
          new DayAggregate(DAY, 7L, null, null, null, null, null, 1800, null, 0);
      assertThat(TrendRules.point("time_distance", true, noDistance))
          .isEqualTo(new Point(DAY, 7L, null, null, null, null, null, 1800, null, null));
    }
  }
}
