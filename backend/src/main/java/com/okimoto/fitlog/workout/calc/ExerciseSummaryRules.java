package com.okimoto.fitlog.workout.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 一覧の種目の要約で、記録方式ごとにどの値を返すか（docs/api.md「WorkoutCard」、docs/test-plan.md 5.4）。
 *
 * <p>最大のセット・合計は SQL で集計する（セットの行を取らないため）。ここでは、集計した値のどれを返すかだけを決める
 */
public final class ExerciseSummaryRules {

  private ExerciseSummaryRules() {}

  public static ExerciseSummary summarize(ExerciseAggregate aggregate) {
    return switch (aggregate.recordType()) {
      case "weight_reps" ->
          // 全セットが同じなら、最大のセットはそのセットと同じ値になる
          summary(
              aggregate,
              aggregate.uniform(),
              aggregate.bestWeightKg(),
              aggregate.bestReps(),
              null,
              null,
              null,
              null);
      case "reps" ->
          aggregate.uniform()
              ? summary(aggregate, true, null, aggregate.bestReps(), null, null, null, null)
              : summary(aggregate, false, null, null, aggregate.totalReps(), null, null, null);
      default ->
          summary(
              aggregate,
              null,
              null,
              null,
              null,
              aggregate.totalDurationSec(),
              aggregate.totalDistanceKm(),
              pace(aggregate));
    };
  }

  /** 1セットで距離があるときだけ、1km あたりの秒数（四捨五入）。距離は 0.01km 以上なので、0 で割ることはない */
  private static Integer pace(ExerciseAggregate aggregate) {
    if (aggregate.setCount() != 1 || aggregate.totalDistanceKm() == null) {
      return null;
    }
    return BigDecimal.valueOf(aggregate.totalDurationSec())
        .divide(aggregate.totalDistanceKm(), 0, RoundingMode.HALF_UP)
        .intValueExact();
  }

  private static ExerciseSummary summary(
      ExerciseAggregate aggregate,
      Boolean uniform,
      BigDecimal weightKg,
      Integer reps,
      Integer totalReps,
      Integer durationSec,
      BigDecimal distanceKm,
      Integer paceSecPerKm) {
    return new ExerciseSummary(
        aggregate.exerciseId(),
        aggregate.name(),
        aggregate.recordType(),
        false,
        aggregate.setCount(),
        uniform,
        weightKg,
        reps,
        totalReps,
        durationSec,
        distanceKm,
        paceSecPerKm);
  }
}
