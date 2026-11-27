package com.okimoto.fitlog.stats.calc;

import com.okimoto.fitlog.stats.calc.StatsRules.Period;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** ランニングの集計（F-83、docs/api.md A-94） */
public final class RunningRules {

  private RunningRules() {}

  /** 集計の期間の単位 */
  public enum Unit {
    WEEK,
    MONTH,
    YEAR
  }

  /**
   * 期間のランニングの集計値。
   *
   * @param distanceKm 走行距離（km）
   * @param durationSec 走行時間（秒）
   * @param runCount 回数
   * @param avgPaceSecPerKm 平均ペース（秒/km）。距離のある記録がなければ null
   */
  public record Summary(
      BigDecimal distanceKm, int durationSec, int runCount, Integer avgPaceSecPerKm) {}

  /**
   * 月ごとの走行距離。
   *
   * @param month 月
   * @param distanceKm 走行距離（km）
   */
  public record MonthDistance(YearMonth month, BigDecimal distanceKm) {}

  /** 値のない月の 0（km は小数第3位まで） */
  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(3);

  /** API の {@code period}（week・month・year）。それ以外なら空 */
  public static Optional<Unit> unitOf(String value) {
    return Arrays.stream(Unit.values())
        .filter(unit -> unit.name().toLowerCase(Locale.ROOT).equals(value))
        .findFirst();
  }

  /** その日を含む期間（週は月曜〜日曜、月は1日〜末日、年は1月1日〜12月31日） */
  public static Period periodOf(Unit unit, LocalDate date) {
    return switch (unit) {
      case WEEK -> StatsRules.week(date);
      case MONTH -> StatsRules.month(YearMonth.from(date));
      case YEAR -> new Period(date.withDayOfYear(1), date.withMonth(12).withDayOfMonth(31));
    };
  }

  /** 前の期間（前の週・月・年） */
  public static Period previous(Unit unit, Period period) {
    return periodOf(unit, period.from().minusDays(1));
  }

  /** ペース（秒/km）。時間 ÷ 距離を秒に四捨五入する。距離が 0 なら null */
  public static Integer pace(int durationSec, BigDecimal distanceKm) {
    if (distanceKm.signum() == 0) {
      return null;
    }
    return BigDecimal.valueOf(durationSec)
        .divide(distanceKm, 0, RoundingMode.HALF_UP)
        .intValueExact();
  }

  /** 期間の集計値。平均ペースは、距離のあるセットの時間の合計 ÷ 距離の合計（1回ごとのペースの平均ではない） */
  public static Summary summary(RunningTotals totals) {
    return new Summary(
        totals.distanceKm(),
        totals.durationSec(),
        totals.runCount(),
        pace(totals.pacedDurationSec(), totals.distanceKm()));
  }

  /** 年間の月ごとの走行距離。1月から12月まで、記録のない月も 0 で並べる */
  public static List<MonthDistance> monthly(int year, List<MonthDistance> months) {
    Map<YearMonth, BigDecimal> byMonth = new HashMap<>();
    months.forEach(month -> byMonth.put(month.month(), month.distanceKm()));
    List<MonthDistance> monthly = new ArrayList<>();
    for (int month = 1; month <= 12; month++) {
      YearMonth key = YearMonth.of(year, month);
      monthly.add(new MonthDistance(key, byMonth.getOrDefault(key, ZERO)));
    }
    return monthly;
  }
}
