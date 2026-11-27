package com.okimoto.fitlog.stats.service;

import com.okimoto.fitlog.common.calc.JapanTime;
import com.okimoto.fitlog.stats.calc.DayTotal;
import com.okimoto.fitlog.stats.calc.StatsRules;
import com.okimoto.fitlog.stats.calc.StatsRules.Daily;
import com.okimoto.fitlog.stats.calc.StatsRules.Period;
import com.okimoto.fitlog.stats.calc.StatsRules.Totals;
import com.okimoto.fitlog.stats.repository.BodyPartCount;
import com.okimoto.fitlog.stats.repository.StatsRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 週間・月間の集計（F-80・F-81、docs/api.md A-90・A-91）。本人の記録だけを、公開範囲に関係なく集計する。
 *
 * <p>消費カロリーの合計（F-86）は段階 5 で足す
 */
@Service
public class StatsService {

  private final StatsRepository repository;
  private final Clock clock;

  public StatsService(StatsRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  /**
   * 週間の集計。
   *
   * @param period 集計した週
   * @param summary その週の集計値
   * @param previous 前の週の集計値
   * @param daily 曜日ごとの値（月曜から7日分）
   * @param bodyParts 部位別のセット数（多い順）
   */
  public record Weekly(
      Period period,
      Totals summary,
      Totals previous,
      List<Daily> daily,
      List<BodyPartCount> bodyParts) {}

  /**
   * 月間の集計。
   *
   * @param period 集計した月
   * @param summary その月の集計値
   * @param previous 前の月の集計値
   * @param weekly 週ごとの値（その月の日だけを数える）
   * @param bodyParts 部位別のセット数（多い順）
   */
  public record Monthly(
      Period period,
      Totals summary,
      Totals previous,
      List<StatsRules.Weekly> weekly,
      List<BodyPartCount> bodyParts) {}

  /**
   * A-90 週間の集計。
   *
   * @param date 週の中のどの日でもよい。null なら今週（日本時間）
   */
  @Transactional(readOnly = true)
  public Weekly weekly(long me, LocalDate date) {
    Period week = StatsRules.week(date == null ? JapanTime.today(clock) : date);
    Period previous = StatsRules.week(week.from().minusDays(1));
    List<DayTotal> days = repository.dailyTotals(me, week);
    return new Weekly(
        week,
        StatsRules.totals(days),
        StatsRules.totals(repository.dailyTotals(me, previous)),
        StatsRules.daily(week, days),
        repository.bodyParts(me, week));
  }

  /**
   * A-91 月間の集計。
   *
   * @param month null なら今月（日本時間）
   */
  @Transactional(readOnly = true)
  public Monthly monthly(long me, YearMonth month) {
    YearMonth target = month == null ? YearMonth.from(JapanTime.today(clock)) : month;
    Period period = StatsRules.month(target);
    List<DayTotal> days = repository.dailyTotals(me, period);
    return new Monthly(
        period,
        StatsRules.totals(days),
        StatsRules.totals(repository.dailyTotals(me, StatsRules.month(target.minusMonths(1)))),
        StatsRules.weekly(target, days),
        repository.bodyParts(me, period));
  }
}
