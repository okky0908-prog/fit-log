package com.okimoto.fitlog.stats.calc;

import static org.assertj.core.api.Assertions.assertThat;

import com.okimoto.fitlog.stats.calc.StatsRules.Daily;
import com.okimoto.fitlog.stats.calc.StatsRules.Period;
import com.okimoto.fitlog.stats.calc.StatsRules.Totals;
import com.okimoto.fitlog.stats.calc.StatsRules.Weekly;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 週間・月間の集計の期間と、日ごとの合計からの集計（docs/test-plan.md 4.4 の週の区切り・月をまたぐ週・うるう年） */
class StatsRulesTest {

  @Nested
  class 週の期間 {

    /** 2026-11-09 は月曜、11-15 は日曜 */
    @Test
    void 月曜から日曜まで_週のどの日を指定しても同じ週() {
      Period week = new Period(day("2026-11-09"), day("2026-11-15"));

      assertThat(StatsRules.week(day("2026-11-09"))).isEqualTo(week);
      assertThat(StatsRules.week(day("2026-11-12"))).isEqualTo(week);
      assertThat(StatsRules.week(day("2026-11-15"))).isEqualTo(week);
    }

    @Test
    void 日曜の次の月曜は次の週() {
      assertThat(StatsRules.week(day("2026-11-16")))
          .isEqualTo(new Period(day("2026-11-16"), day("2026-11-22")));
    }

    @Test
    void 年をまたぐ週() {
      assertThat(StatsRules.week(day("2027-01-01")))
          .isEqualTo(new Period(day("2026-12-28"), day("2027-01-03")));
    }
  }

  @Nested
  class 月の期間 {

    @Test
    void 一日から末日まで() {
      assertThat(StatsRules.month(YearMonth.of(2026, 11)))
          .isEqualTo(new Period(day("2026-11-01"), day("2026-11-30")));
      assertThat(StatsRules.month(YearMonth.of(2026, 12)))
          .isEqualTo(new Period(day("2026-12-01"), day("2026-12-31")));
    }

    @Test
    void うるう年の二月は29日まで() {
      assertThat(StatsRules.month(YearMonth.of(2028, 2)))
          .isEqualTo(new Period(day("2028-02-01"), day("2028-02-29")));
      assertThat(StatsRules.month(YearMonth.of(2027, 2)).to()).isEqualTo(day("2027-02-28"));
    }
  }

  @Nested
  class 集計値 {

    @Test
    void 日ごとの合計を足し_記録のある日の数を数える() {
      Totals totals =
          StatsRules.totals(
              List.of(
                  total("2026-11-09", "3200.000", 0, "0.000"),
                  total("2026-11-11", "1250.500", 1800, "5.000"),
                  total("2026-11-14", "0.000", 3900, "10.250")));

      assertThat(totals.trainingDays()).isEqualTo(3);
      assertThat(totals.totalVolumeKg()).isEqualByComparingTo("4450.500");
      assertThat(totals.totalDurationSec()).isEqualTo(5700);
      assertThat(totals.totalDistanceKm()).isEqualByComparingTo("15.250");
    }

    @Test
    void 記録がなければ0() {
      Totals totals = StatsRules.totals(List.of());

      assertThat(totals.trainingDays()).isZero();
      assertThat(totals.totalVolumeKg()).isEqualByComparingTo("0");
      assertThat(totals.totalDurationSec()).isZero();
      assertThat(totals.totalDistanceKm()).isEqualByComparingTo("0");
    }
  }

  @Nested
  class 曜日ごと {

    @Test
    void 記録のない日も0で7日分を_月曜から並べる() {
      List<Daily> daily =
          StatsRules.daily(
              StatsRules.week(day("2026-11-09")),
              List.of(
                  total("2026-11-15", "100.000", 0, "0.000"),
                  total("2026-11-09", "3200.000", 600, "0.000")));

      assertThat(daily).hasSize(7);
      assertThat(daily).extracting(Daily::date).first().isEqualTo(day("2026-11-09"));
      assertThat(daily).extracting(Daily::date).last().isEqualTo(day("2026-11-15"));
      assertThat(daily.get(0).volumeKg()).isEqualByComparingTo("3200.000");
      assertThat(daily.get(0).durationSec()).isEqualTo(600);
      assertThat(daily.get(1).volumeKg()).isEqualByComparingTo("0");
      assertThat(daily.get(1).durationSec()).isZero();
      assertThat(daily.get(6).volumeKg()).isEqualByComparingTo("100.000");
    }
  }

  @Nested
  class 週ごと {

    /** 2026-11-01 は日曜なので、最初の週は 10-26（月）から。11-30（月）からの週は 12-06 まで */
    @Test
    void その月に含まれる週を_月曜の日付で並べる() {
      List<Weekly> weekly = StatsRules.weekly(YearMonth.of(2026, 11), List.of());

      assertThat(weekly)
          .extracting(Weekly::weekStart)
          .containsExactly(
              day("2026-10-26"),
              day("2026-11-02"),
              day("2026-11-09"),
              day("2026-11-16"),
              day("2026-11-23"),
              day("2026-11-30"));
      assertThat(weekly.get(0).volumeKg()).isEqualByComparingTo("0");
      assertThat(weekly.get(0).durationSec()).isZero();
      assertThat(weekly.get(0).distanceKm()).isEqualByComparingTo("0");
      assertThat(weekly.get(0).trainingDays()).isZero();
    }

    @Test
    void 月曜から始まる月は_最初の週がその月の1日から() {
      // 2027-02-01 は月曜。2027-02 は 02-22（月）〜 02-28（日）で終わる
      assertThat(StatsRules.weekly(YearMonth.of(2027, 2), List.of()))
          .extracting(Weekly::weekStart)
          .containsExactly(
              day("2027-02-01"), day("2027-02-08"), day("2027-02-15"), day("2027-02-22"));
    }

    @Test
    void 日ごとの合計を週にまとめる_日曜と月曜は別の週() {
      List<Weekly> weekly =
          StatsRules.weekly(
              YearMonth.of(2026, 11),
              List.of(
                  total("2026-11-01", "1000.000", 0, "0.000"),
                  total("2026-11-08", "500.000", 1800, "5.000"),
                  total("2026-11-09", "200.000", 600, "2.000"),
                  total("2026-11-15", "300.000", 0, "0.000"),
                  total("2026-11-30", "50.000", 0, "1.000")));

      assertThat(weekly.get(0))
          .isEqualTo(new Weekly(day("2026-10-26"), new BigDecimal("1000.000"), 0, zero(), 1));
      assertThat(weekly.get(1))
          .isEqualTo(
              new Weekly(
                  day("2026-11-02"), new BigDecimal("500.000"), 1800, new BigDecimal("5.000"), 1));
      assertThat(weekly.get(2))
          .isEqualTo(
              new Weekly(
                  day("2026-11-09"), new BigDecimal("500.000"), 600, new BigDecimal("2.000"), 2));
      assertThat(weekly.get(5).trainingDays()).isEqualTo(1);
      assertThat(weekly.get(5).distanceKm()).isEqualByComparingTo("1.000");
    }
  }

  private static LocalDate day(String value) {
    return LocalDate.parse(value);
  }

  private static BigDecimal zero() {
    return new BigDecimal("0.000");
  }

  private static DayTotal total(String date, String volumeKg, int durationSec, String distanceKm) {
    return new DayTotal(
        day(date), new BigDecimal(volumeKg), durationSec, new BigDecimal(distanceKm));
  }
}
