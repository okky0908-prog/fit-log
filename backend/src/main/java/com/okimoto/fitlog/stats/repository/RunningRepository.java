package com.okimoto.fitlog.stats.repository;

import static com.okimoto.fitlog.jooq.Tables.EXERCISES;
import static com.okimoto.fitlog.jooq.Tables.WORKOUTS;
import static com.okimoto.fitlog.jooq.Tables.WORKOUT_EXERCISES;
import static com.okimoto.fitlog.jooq.Tables.WORKOUT_SETS;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.sum;
import static org.jooq.impl.DSL.trunc;

import com.okimoto.fitlog.stats.calc.RunningRules.MonthDistance;
import com.okimoto.fitlog.stats.calc.RunningTotals;
import com.okimoto.fitlog.stats.calc.StatsRules.Period;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.DatePart;
import org.jooq.Field;
import org.jooq.Record4;
import org.jooq.Records;
import org.jooq.SelectOnConditionStep;
import org.jooq.SortField;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

/**
 * ランニングの集計の SQL（docs/database.md「ランニングの集計」、F-83）。
 *
 * <p>ランニングの種目（{@code exercises.is_running}）のセットだけを、本人の記録から、公開範囲に関係なく集計する。1セット ＝ 1回の走行
 */
@Repository
public class RunningRepository {

  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(3);

  /** 最速ペースの対象の距離（km）。マイル表示でも、判定は保存している km で行う */
  private static final BigDecimal BEST_PACE_MIN_KM = BigDecimal.ONE;

  /** 1回の走行のペース（秒/km） */
  private static final Field<BigDecimal> PACE =
      WORKOUT_SETS.DURATION_SEC.cast(SQLDataType.NUMERIC).div(WORKOUT_SETS.DISTANCE_KM);

  private final DSLContext dsl;

  public RunningRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** 期間の合計（走行距離・走行時間・回数と、平均ペースに使う、距離のあるセットの時間の合計） */
  public RunningTotals totals(long me, Period period) {
    return dsl.select(
            coalesce(sum(WORKOUT_SETS.DISTANCE_KM), inline(ZERO)),
            coalesce(sum(WORKOUT_SETS.DURATION_SEC), inline(BigDecimal.ZERO))
                .cast(SQLDataType.INTEGER),
            count(),
            coalesce(
                    sum(WORKOUT_SETS.DURATION_SEC)
                        .filterWhere(WORKOUT_SETS.DISTANCE_KM.isNotNull()),
                    inline(BigDecimal.ZERO))
                .cast(SQLDataType.INTEGER))
        .from(WORKOUTS)
        .join(WORKOUT_EXERCISES)
        .on(WORKOUT_EXERCISES.WORKOUT_ID.eq(WORKOUTS.ID))
        .join(EXERCISES)
        .on(EXERCISES.ID.eq(WORKOUT_EXERCISES.EXERCISE_ID))
        .join(WORKOUT_SETS)
        .on(WORKOUT_SETS.WORKOUT_EXERCISE_ID.eq(WORKOUT_EXERCISES.ID))
        .where(runsOf(me, period))
        .fetchSingle(Records.mapping(RunningTotals::new));
  }

  /** 最速ペースの1回：距離が 1km 以上のセットのうち、ペースが一番速い。同じなら距離が長い方、それも同じなら実施日が新しい方 */
  public Optional<RunRecord> bestPace(long me, Period period) {
    return oneRun(
        runsOf(me, period).and(WORKOUT_SETS.DISTANCE_KM.ge(BEST_PACE_MIN_KM)),
        PACE.asc(),
        WORKOUT_SETS.DISTANCE_KM.desc());
  }

  /** 最長距離の1回：距離が一番長い。同じなら時間が短い方、それも同じなら実施日が新しい方 */
  public Optional<RunRecord> longestRun(long me, Period period) {
    return oneRun(
        runsOf(me, period).and(WORKOUT_SETS.DISTANCE_KM.isNotNull()),
        WORKOUT_SETS.DISTANCE_KM.desc(),
        WORKOUT_SETS.DURATION_SEC.asc());
  }

  /** 期間の、月ごとの走行距離（記録のある月だけ。月の順） */
  public List<MonthDistance> monthlyDistances(long me, Period period) {
    Field<LocalDate> month = trunc(WORKOUTS.PERFORMED_ON, DatePart.MONTH);
    return dsl.select(month, coalesce(sum(WORKOUT_SETS.DISTANCE_KM), inline(ZERO)))
        .from(WORKOUTS)
        .join(WORKOUT_EXERCISES)
        .on(WORKOUT_EXERCISES.WORKOUT_ID.eq(WORKOUTS.ID))
        .join(EXERCISES)
        .on(EXERCISES.ID.eq(WORKOUT_EXERCISES.EXERCISE_ID))
        .join(WORKOUT_SETS)
        .on(WORKOUT_SETS.WORKOUT_EXERCISE_ID.eq(WORKOUT_EXERCISES.ID))
        .where(runsOf(me, period))
        .groupBy(month)
        .orderBy(month)
        .fetch(
            Records.mapping(
                (first, distance) -> new MonthDistance(YearMonth.from(first), distance)));
  }

  /** 条件に合う走行を、並び順の先頭の1回だけ。最後は実施日・投稿日時が新しい方（それも同じなら後のセット） */
  private Optional<RunRecord> oneRun(Condition condition, SortField<?> first, SortField<?> second) {
    return runRecords()
        .where(condition)
        .orderBy(
            first,
            second,
            WORKOUTS.PERFORMED_ON.desc(),
            WORKOUTS.CREATED_AT.desc(),
            WORKOUT_SETS.ID.desc())
        .limit(1)
        .fetchOptional(Records.mapping(RunRecord::new));
  }

  private SelectOnConditionStep<Record4<Long, LocalDate, BigDecimal, Integer>> runRecords() {
    return dsl.select(
            WORKOUTS.ID, WORKOUTS.PERFORMED_ON, WORKOUT_SETS.DISTANCE_KM, WORKOUT_SETS.DURATION_SEC)
        .from(WORKOUTS)
        .join(WORKOUT_EXERCISES)
        .on(WORKOUT_EXERCISES.WORKOUT_ID.eq(WORKOUTS.ID))
        .join(EXERCISES)
        .on(EXERCISES.ID.eq(WORKOUT_EXERCISES.EXERCISE_ID))
        .join(WORKOUT_SETS)
        .on(WORKOUT_SETS.WORKOUT_EXERCISE_ID.eq(WORKOUT_EXERCISES.ID));
  }

  /** 本人の、期間の中（両端の日を含む）の、ランニングの種目のセット。公開範囲は見ない */
  private static Condition runsOf(long me, Period period) {
    return WORKOUTS
        .USER_ID
        .eq(me)
        .and(WORKOUTS.PERFORMED_ON.between(period.from(), period.to()))
        .and(EXERCISES.IS_RUNNING.isTrue());
  }
}
