package com.okimoto.fitlog.stats.calc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/** 種目ごとの推移の期間と、記録方式ごとの点の値（F-84、docs/api.md A-96） */
public final class TrendRules {

  private TrendRules() {}

  /** 期間（直近3か月・1年・全期間） */
  public enum Range {
    THREE_MONTHS,
    ONE_YEAR,
    ALL
  }

  /** A-96 の点 */
  public record Point(
      LocalDate date,
      long workoutId,
      BigDecimal maxWeightKg,
      BigDecimal maxE1rmKg,
      Integer maxWeightReps,
      Integer totalReps,
      Integer maxReps,
      Integer totalDurationSec,
      BigDecimal totalDistanceKm,
      Integer paceSecPerKm) {}

  /** URL の {@code range}（{@code 3m}・{@code 1y}・{@code all}。小文字だけ）。それ以外は空 */
  public static Optional<Range> rangeOf(String value) {
    return switch (value) {
      case "3m" -> Optional.of(Range.THREE_MONTHS);
      case "1y" -> Optional.of(Range.ONE_YEAR);
      case "all" -> Optional.of(Range.ALL);
      default -> Optional.empty();
    };
  }

  /**
   * 期間の始まりの日（この日を含む）。3か月・1年前に同じ日がなければ、その月の末日（{@link LocalDate#minusMonths}）。全期間は空
   *
   * @param today 今日（日本時間）
   */
  public static Optional<LocalDate> fromOf(Range range, LocalDate today) {
    return switch (range) {
      case THREE_MONTHS -> Optional.of(today.minusMonths(3));
      case ONE_YEAR -> Optional.of(today.minusYears(1));
      case ALL -> Optional.empty();
    };
  }

  /**
   * その日の点。記録方式で使わない値は null にする（重量 × 回数：最大重量・推定 1RM・最大重量の回数、回数のみ：合計回数・最大回数、時間・距離：合計時間・合計距離。
   * ランニングはペースも）
   *
   * @param recordType 種目の記録方式（weight_reps・reps・time_distance）
   * @param isRunning ランニングの種目か
   */
  public static Point point(String recordType, boolean isRunning, DayAggregate day) {
    return switch (recordType) {
      case "weight_reps" ->
          new Point(
              day.date(),
              day.workoutId(),
              day.maxWeightKg(),
              day.maxE1rmKg(),
              day.maxWeightReps(),
              null,
              null,
              null,
              null,
              null);
      case "reps" ->
          new Point(
              day.date(),
              day.workoutId(),
              null,
              null,
              null,
              day.totalReps(),
              day.maxReps(),
              null,
              null,
              null);
      default ->
          new Point(
              day.date(),
              day.workoutId(),
              null,
              null,
              null,
              null,
              null,
              day.totalDurationSec(),
              day.totalDistanceKm(),
              isRunning && day.totalDistanceKm() != null
                  ? RunningRules.pace(day.pacedDurationSec(), day.totalDistanceKm())
                  : null);
    };
  }
}
