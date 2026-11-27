package com.okimoto.fitlog.stats.repository;

import static com.okimoto.fitlog.jooq.Tables.EXERCISES;
import static com.okimoto.fitlog.jooq.Tables.WORKOUTS;
import static com.okimoto.fitlog.jooq.Tables.WORKOUT_EXERCISES;
import static com.okimoto.fitlog.jooq.Tables.WORKOUT_SETS;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.sum;

import com.okimoto.fitlog.stats.calc.DayTotal;
import com.okimoto.fitlog.stats.calc.StatsRules.Period;
import java.math.BigDecimal;
import java.util.List;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Records;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

/**
 * 週間・月間の集計の SQL（docs/database.md「週間・月間の集計」）。
 *
 * <p>本人の記録だけを、公開範囲に関係なく集計する。記録のない日を 0 で埋める・週にまとめる・期間の合計を出すのは {@code StatsRules} で行う（SQL
 * は記録のある日だけを返す）
 */
@Repository
public class StatsRepository {

  /** 部位の決まった順（docs/features.md の部位の並び）。セット数が同じ部位はこの順に並べる */
  private static final String[] BODY_PART_ORDER = {
    "chest", "back", "legs", "shoulders", "arms", "abs", "full_body", "cardio"
  };

  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(3);

  private final DSLContext dsl;

  public StatsRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** 期間の、記録のある日ごとの合計（日付の順）。総挙上量は重量のあるセットの重量 × 回数、運動時間と距離は時間・距離のセットの合計 */
  public List<DayTotal> dailyTotals(long me, Period period) {
    Field<BigDecimal> volume =
        coalesce(sum(WORKOUT_SETS.WEIGHT_KG.mul(WORKOUT_SETS.REPS)), inline(ZERO));
    Field<Integer> duration =
        coalesce(sum(WORKOUT_SETS.DURATION_SEC), inline(BigDecimal.ZERO)).cast(SQLDataType.INTEGER);
    Field<BigDecimal> distance = coalesce(sum(WORKOUT_SETS.DISTANCE_KM), inline(ZERO));
    return dsl.select(WORKOUTS.PERFORMED_ON, volume, duration, distance)
        .from(WORKOUTS)
        .join(WORKOUT_EXERCISES)
        .on(WORKOUT_EXERCISES.WORKOUT_ID.eq(WORKOUTS.ID))
        .join(WORKOUT_SETS)
        .on(WORKOUT_SETS.WORKOUT_EXERCISE_ID.eq(WORKOUT_EXERCISES.ID))
        .where(mine(me, period))
        .groupBy(WORKOUTS.PERFORMED_ON)
        .orderBy(WORKOUTS.PERFORMED_ON)
        .fetch(Records.mapping(DayTotal::new));
  }

  /** 期間の部位別のセット数。セット数の多い順、同じなら部位の決まった順。セットのない部位は返さない */
  public List<BodyPartCount> bodyParts(long me, Period period) {
    Field<Integer> setCount = count();
    return dsl.select(EXERCISES.BODY_PART, setCount)
        .from(WORKOUTS)
        .join(WORKOUT_EXERCISES)
        .on(WORKOUT_EXERCISES.WORKOUT_ID.eq(WORKOUTS.ID))
        .join(WORKOUT_SETS)
        .on(WORKOUT_SETS.WORKOUT_EXERCISE_ID.eq(WORKOUT_EXERCISES.ID))
        .join(EXERCISES)
        .on(EXERCISES.ID.eq(WORKOUT_EXERCISES.EXERCISE_ID))
        .where(mine(me, period))
        .groupBy(EXERCISES.BODY_PART)
        .orderBy(setCount.desc(), EXERCISES.BODY_PART.sortAsc(BODY_PART_ORDER))
        .fetch(Records.mapping(BodyPartCount::new));
  }

  /** 本人の、期間の中（両端の日を含む）の記録。公開範囲は見ない */
  private static Condition mine(long me, Period period) {
    return WORKOUTS.USER_ID.eq(me).and(WORKOUTS.PERFORMED_ON.between(period.from(), period.to()));
  }
}
