package com.okimoto.fitlog.stats.controller;

import com.okimoto.fitlog.auth.config.AuthenticatedUser;
import com.okimoto.fitlog.common.controller.ApiException;
import com.okimoto.fitlog.common.controller.ErrorCode;
import com.okimoto.fitlog.image.service.ImageUrls;
import com.okimoto.fitlog.stats.calc.RunningRules;
import com.okimoto.fitlog.stats.calc.RunningRules.Unit;
import com.okimoto.fitlog.stats.calc.StatsRules;
import com.okimoto.fitlog.stats.calc.StatsRules.Daily;
import com.okimoto.fitlog.stats.calc.StatsRules.Totals;
import com.okimoto.fitlog.stats.repository.BodyPartCount;
import com.okimoto.fitlog.stats.repository.RunRecord;
import com.okimoto.fitlog.stats.service.StatsService;
import com.okimoto.fitlog.workout.controller.WorkoutCardResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 週間・月間の集計・カレンダー・ランニングの集計（docs/api.md A-90〜A-94）。本人のデータだけを返す */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

  private final StatsService statsService;
  private final ImageUrls imageUrls;

  public StatsController(StatsService statsService, ImageUrls imageUrls) {
    this.statsService = statsService;
    this.imageUrls = imageUrls;
  }

  /**
   * A-92 の応答。
   *
   * @param month 表示する月（YYYY-MM）
   * @param dates 記録がある日
   */
  public record CalendarResponse(String month, List<LocalDate> dates) {}

  /** A-92 カレンダー（記録がある日）。month は YYYY-MM。なければ今月 */
  @GetMapping("/calendar")
  public CalendarResponse calendar(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
    StatsService.Calendar calendar = statsService.calendar(user.id(), month);
    return new CalendarResponse(calendar.month().toString(), calendar.dates());
  }

  /** A-93 その日の自分の記録（投稿日時の順にすべて。ページングしない）。date は YYYY-MM-DD。なければ今日 */
  @GetMapping("/calendar/workouts")
  public List<WorkoutCardResponse> dayWorkouts(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    StatsService.DayWorkouts day = statsService.dayWorkouts(user.id(), date);
    return day.items().stream()
        .map(row ->
            WorkoutCardResponse.from(
                row, day.personalBests(), user.id(), imageUrls::urlOf, imageUrls::iconUrlOf))
        .toList();
  }

  /**
   * 期間の集計値（kg・km・秒）。
   *
   * <p>まだ作っていない機能の項目は、決まった値を返す：{@code caloriesKcal} は null、{@code caloriesExcludedWorkoutCount} は
   * 0（段階 5）
   *
   * @param trainingDays トレーニング日数
   * @param totalVolumeKg 総挙上量
   * @param totalDurationSec 運動時間
   * @param totalDistanceKm 距離
   * @param caloriesKcal 消費カロリーの合計。計算できた記録がなければ null
   * @param caloriesExcludedWorkoutCount 消費カロリーを計算できなかった記録の数
   */
  public record Summary(
      int trainingDays,
      BigDecimal totalVolumeKg,
      int totalDurationSec,
      BigDecimal totalDistanceKm,
      Integer caloriesKcal,
      int caloriesExcludedWorkoutCount) {

    static Summary of(Totals totals) {
      return new Summary(
          totals.trainingDays(),
          totals.totalVolumeKg(),
          totals.totalDurationSec(),
          totals.totalDistanceKm(),
          null,
          0);
    }
  }

  /** A-90 の応答 */
  public record WeeklyResponse(
      LocalDate from,
      LocalDate to,
      Summary summary,
      Summary previous,
      List<Daily> daily,
      List<BodyPartCount> bodyParts) {}

  /** A-91 の応答 */
  public record MonthlyResponse(
      LocalDate from,
      LocalDate to,
      Summary summary,
      Summary previous,
      List<StatsRules.Weekly> weekly,
      List<BodyPartCount> bodyParts) {}

  /**
   * 1回の走行（最速ペース・最長距離）。
   *
   * @param workoutId 記録（押すと詳細を開く）
   * @param performedOn 実施日
   * @param distanceKm 距離（km）
   * @param durationSec 時間（秒）
   * @param paceSecPerKm ペース（秒/km）
   */
  public record Run(
      long workoutId,
      LocalDate performedOn,
      BigDecimal distanceKm,
      int durationSec,
      Integer paceSecPerKm) {

    static Run of(RunRecord run) {
      return run == null
          ? null
          : new Run(
              run.workoutId(),
              run.performedOn(),
              run.distanceKm(),
              run.durationSec(),
              RunningRules.pace(run.durationSec(), run.distanceKm()));
    }
  }

  /**
   * 月ごとの走行距離。
   *
   * @param month 月（YYYY-MM）
   * @param distanceKm 走行距離（km）
   */
  public record MonthlyDistance(String month, BigDecimal distanceKm) {}

  /** A-94 の応答 */
  public record RunningResponse(
      LocalDate from,
      LocalDate to,
      RunningRules.Summary summary,
      RunningRules.Summary previous,
      Run bestPace,
      Run longestRun,
      List<MonthlyDistance> monthly) {}

  /** A-94 ランニングの集計。period は week・month・year（必須。それ以外は 400）。date は期間の中のどの日でもよい。なければ今日を含む期間 */
  @GetMapping("/running")
  public RunningResponse running(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam String period,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    Unit unit =
        RunningRules.unitOf(period)
            .orElseThrow(() -> new ApiException(ErrorCode.MALFORMED_REQUEST));
    StatsService.Running running = statsService.running(user.id(), unit, date);
    return new RunningResponse(
        running.period().from(),
        running.period().to(),
        running.summary(),
        running.previous(),
        Run.of(running.bestPace()),
        Run.of(running.longestRun()),
        running.monthly().stream()
            .map(month -> new MonthlyDistance(month.month().toString(), month.distanceKm()))
            .toList());
  }

  /** A-90 週間の集計。date は週の中のどの日でもよい（YYYY-MM-DD）。なければ今週 */
  @GetMapping("/weekly")
  public WeeklyResponse weekly(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    StatsService.Weekly stats = statsService.weekly(user.id(), date);
    return new WeeklyResponse(
        stats.period().from(),
        stats.period().to(),
        Summary.of(stats.summary()),
        Summary.of(stats.previous()),
        stats.daily(),
        stats.bodyParts());
  }

  /** A-91 月間の集計。month は YYYY-MM。なければ今月 */
  @GetMapping("/monthly")
  public MonthlyResponse monthly(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
    StatsService.Monthly stats = statsService.monthly(user.id(), month);
    return new MonthlyResponse(
        stats.period().from(),
        stats.period().to(),
        Summary.of(stats.summary()),
        Summary.of(stats.previous()),
        stats.weekly(),
        stats.bodyParts());
  }
}
