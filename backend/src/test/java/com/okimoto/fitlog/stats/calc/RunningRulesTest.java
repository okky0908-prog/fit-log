package com.okimoto.fitlog.stats.calc;

import static org.assertj.core.api.Assertions.assertThat;

import com.okimoto.fitlog.stats.calc.RunningRules.MonthDistance;
import com.okimoto.fitlog.stats.calc.RunningRules.Summary;
import com.okimoto.fitlog.stats.calc.RunningRules.Unit;
import com.okimoto.fitlog.stats.calc.StatsRules.Period;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** ランニングの集計の期間・ペース・月ごとの値（docs/test-plan.md 5.11・4.4） */
class RunningRulesTest {

  @Nested
  class 期間 {

    @Test
    void 単位はweek_month_yearだけ() {
      assertThat(RunningRules.unitOf("week")).contains(Unit.WEEK);
      assertThat(RunningRules.unitOf("month")).contains(Unit.MONTH);
      assertThat(RunningRules.unitOf("year")).contains(Unit.YEAR);
      assertThat(RunningRules.unitOf("WEEK")).isEmpty();
      assertThat(RunningRules.unitOf("day")).isEmpty();
      assertThat(RunningRules.unitOf(null)).isEmpty();
    }

    @Test
    void 週と月はその日を含む週と月() {
      assertThat(RunningRules.periodOf(Unit.WEEK, day("2026-11-12")))
          .isEqualTo(new Period(day("2026-11-09"), day("2026-11-15")));
      assertThat(RunningRules.periodOf(Unit.MONTH, day("2026-11-12")))
          .isEqualTo(new Period(day("2026-11-01"), day("2026-11-30")));
    }

    @Test
    void 年は1月1日から12月31日() {
      assertThat(RunningRules.periodOf(Unit.YEAR, day("2026-11-12")))
          .isEqualTo(new Period(day("2026-01-01"), day("2026-12-31")));
    }

    @Test
    void 前の期間() {
      assertThat(RunningRules.previous(Unit.WEEK, new Period(day("2026-11-09"), day("2026-11-15"))))
          .isEqualTo(new Period(day("2026-11-02"), day("2026-11-08")));
      assertThat(
              RunningRules.previous(Unit.MONTH, new Period(day("2026-03-01"), day("2026-03-31"))))
          .isEqualTo(new Period(day("2026-02-01"), day("2026-02-28")));
      assertThat(RunningRules.previous(Unit.YEAR, new Period(day("2026-01-01"), day("2026-12-31"))))
          .isEqualTo(new Period(day("2025-01-01"), day("2025-12-31")));
    }
  }

  @Nested
  class ペース {

    @Test
    void 時間を距離で割り_秒に四捨五入する() {
      assertThat(RunningRules.pace(1800, km("5.000"))).isEqualTo(360);
      // 1000 ÷ 3 = 333.33… → 333、1001 ÷ 2 = 500.5 → 501
      assertThat(RunningRules.pace(1000, km("3.000"))).isEqualTo(333);
      assertThat(RunningRules.pace(1001, km("2.000"))).isEqualTo(501);
    }

    @Test
    void 距離が0ならnull() {
      assertThat(RunningRules.pace(1800, km("0.000"))).isNull();
    }
  }

  @Nested
  class 集計値 {

    @Test
    void 平均ペースは_距離のあるセットの時間の合計を距離の合計で割る() {
      // 距離のあるセットが 1800秒 5km と 1500秒 4km、距離のないセットが 600秒 → 3300 ÷ 9 = 366.66… → 367
      Summary summary = RunningRules.summary(new RunningTotals(km("9.000"), 3900, 3, 3300));

      assertThat(summary).isEqualTo(new Summary(km("9.000"), 3900, 3, 367));
    }

    @Test
    void 距離のある記録がなければ_平均ペースはnull() {
      assertThat(RunningRules.summary(new RunningTotals(km("0.000"), 600, 1, 0)))
          .isEqualTo(new Summary(km("0.000"), 600, 1, null));
    }
  }

  @Nested
  class 月ごと {

    @Test
    void 年の12か月を_記録のない月も0で並べる() {
      List<MonthDistance> monthly =
          RunningRules.monthly(
              2026,
              List.of(
                  new MonthDistance(YearMonth.of(2026, 3), km("12.500")),
                  new MonthDistance(YearMonth.of(2026, 12), km("5.000"))));

      assertThat(monthly).hasSize(12);
      assertThat(monthly.getFirst())
          .isEqualTo(new MonthDistance(YearMonth.of(2026, 1), km("0.000")));
      assertThat(monthly.get(2)).isEqualTo(new MonthDistance(YearMonth.of(2026, 3), km("12.500")));
      assertThat(monthly.getLast())
          .isEqualTo(new MonthDistance(YearMonth.of(2026, 12), km("5.000")));
    }
  }

  private static LocalDate day(String value) {
    return LocalDate.parse(value);
  }

  private static BigDecimal km(String value) {
    return new BigDecimal(value);
  }
}
