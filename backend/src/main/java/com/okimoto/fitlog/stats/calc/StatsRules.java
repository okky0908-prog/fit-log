package com.okimoto.fitlog.stats.calc;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 週間・月間の集計の期間と、日ごとの合計からの集計（F-80・F-81、docs/api.md「集計」） */
public final class StatsRules {

  private StatsRules() {}

  /**
   * 集計の期間（両端を含む）。
   *
   * @param from 最初の日
   * @param to 最後の日
   */
  public record Period(LocalDate from, LocalDate to) {}

  /**
   * 期間の集計値。
   *
   * @param trainingDays トレーニング日数（記録がある日の数）
   * @param totalVolumeKg 総挙上量（kg）
   * @param totalDurationSec 運動時間（秒）
   * @param totalDistanceKm 距離（km）
   */
  public record Totals(
      int trainingDays,
      BigDecimal totalVolumeKg,
      int totalDurationSec,
      BigDecimal totalDistanceKm) {}

  /**
   * 週間の曜日ごとの値。
   *
   * @param date 日付
   * @param volumeKg 総挙上量（kg）
   * @param durationSec 運動時間（秒）
   */
  public record Daily(LocalDate date, BigDecimal volumeKg, int durationSec) {}

  /**
   * 月間の週ごとの値（その月の日だけを数える）。
   *
   * @param weekStart 週の初めの月曜（前の月の日のこともある）
   * @param volumeKg 総挙上量（kg）
   * @param durationSec 運動時間（秒）
   * @param distanceKm 距離（km）
   * @param trainingDays トレーニング日数
   */
  public record Weekly(
      LocalDate weekStart,
      BigDecimal volumeKg,
      int durationSec,
      BigDecimal distanceKm,
      int trainingDays) {}

  /** 値のない日・週の 0（kg・km は小数第3位まで） */
  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(3);

  /** その日を含む週（月曜〜日曜） */
  public static Period week(LocalDate date) {
    LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    return new Period(monday, monday.plusDays(6));
  }

  /** その月（1日〜末日） */
  public static Period month(YearMonth month) {
    return new Period(month.atDay(1), month.atEndOfMonth());
  }

  /** 期間の集計値。days は期間の中の、記録のある日だけ（1日に1件） */
  public static Totals totals(List<DayTotal> days) {
    BigDecimal volume = ZERO;
    int duration = 0;
    BigDecimal distance = ZERO;
    for (DayTotal day : days) {
      volume = volume.add(day.volumeKg());
      duration += day.durationSec();
      distance = distance.add(day.distanceKm());
    }
    return new Totals(days.size(), volume, duration, distance);
  }

  /** 週間の曜日ごとの値。記録のない日も 0 で、月曜から7日分（グラフの棒の位置がずれないように） */
  public static List<Daily> daily(Period week, List<DayTotal> days) {
    Map<LocalDate, DayTotal> byDate = byDate(days);
    List<Daily> daily = new ArrayList<>();
    for (LocalDate date = week.from(); !date.isAfter(week.to()); date = date.plusDays(1)) {
      DayTotal day = byDate.get(date);
      daily.add(
          day == null
              ? new Daily(date, ZERO, 0)
              : new Daily(date, day.volumeKg(), day.durationSec()));
    }
    return daily;
  }

  /** 月間の週ごとの値。その月の日を含む週を、月曜の日付の順にすべて返す（記録のない週も 0）。月をまたぐ週は、その月の日だけを数える（days にはその月の日だけを渡す） */
  public static List<Weekly> weekly(YearMonth month, List<DayTotal> days) {
    Map<LocalDate, List<DayTotal>> byWeek = new LinkedHashMap<>();
    for (LocalDate monday = week(month.atDay(1)).from();
        !monday.isAfter(month.atEndOfMonth());
        monday = monday.plusWeeks(1)) {
      byWeek.put(monday, new ArrayList<>());
    }
    for (DayTotal day : days) {
      byWeek.get(week(day.date()).from()).add(day);
    }
    List<Weekly> weekly = new ArrayList<>();
    byWeek.forEach((monday, inWeek) -> {
      Totals totals = totals(inWeek);
      weekly.add(
          new Weekly(
              monday,
              totals.totalVolumeKg(),
              totals.totalDurationSec(),
              totals.totalDistanceKm(),
              totals.trainingDays()));
    });
    return weekly;
  }

  private static Map<LocalDate, DayTotal> byDate(List<DayTotal> days) {
    Map<LocalDate, DayTotal> byDate = new HashMap<>();
    days.forEach(day -> byDate.put(day.date(), day));
    return byDate;
  }
}
