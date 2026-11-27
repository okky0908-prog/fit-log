package com.okimoto.fitlog.stats.repository;

import static com.okimoto.fitlog.jooq.Tables.EXERCISES;
import static com.okimoto.fitlog.jooq.Tables.WORKOUTS;
import static com.okimoto.fitlog.jooq.Tables.WORKOUT_EXERCISES;
import static com.okimoto.fitlog.jooq.Tables.WORKOUT_SETS;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.partitionBy;
import static org.jooq.impl.DSL.round;
import static org.jooq.impl.DSL.rowNumber;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.sum;
import static org.jooq.impl.DSL.when;

import com.okimoto.fitlog.exercise.repository.ExerciseRepository;
import com.okimoto.fitlog.stats.calc.DayAggregate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Records;
import org.jooq.SortField;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

/**
 * 種目ごとの推移の SQL（docs/database.md「種目ごとの推移」、F-84・F-85）。本人の記録だけを、公開範囲に関係なく集計する。
 *
 * <p>推定 1RM は Epley 式（重量 ×（1 + 回数 ÷ 30））で、回数が1〜10回のセットだけから計算する。1回のセットは重量そのもの。値は kg の小数第3位に四捨五入する
 */
@Repository
public class TrendRepository {

  /** 推定 1RM（小数第3位に四捨五入）。重量 ×（30 + 回数）÷ 30 として、割り算を1回にする */
  private static final Field<BigDecimal> E1RM =
      round(
          when(WORKOUT_SETS.REPS.eq(1), WORKOUT_SETS.WEIGHT_KG)
              .otherwise(WORKOUT_SETS.WEIGHT_KG.mul(WORKOUT_SETS.REPS.add(30)).div(inline(30))),
          3);

  /** 推定 1RM の対象のセット */
  private static final Condition E1RM_TARGET = WORKOUT_SETS.REPS.between(1, 10);

  /** 自己ベストの対象のセット（重量があり、1回以上挙げた） */
  private static final Condition LIFTED =
      WORKOUT_SETS.WEIGHT_KG.isNotNull().and(WORKOUT_SETS.REPS.ge(1));

  private final DSLContext dsl;

  public TrendRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** A-95 自分が1回以上記録した種目と、最後に記録した実施日。並びは種目の検索（A-30）と同じ */
  public List<UsedExercise> usedExercises(long me) {
    Field<LocalDate> last = max(WORKOUTS.PERFORMED_ON).as("last_performed_on");
    return dsl.select(EXERCISES.fields())
        .select(last)
        .from(EXERCISES)
        .join(WORKOUT_EXERCISES)
        .on(WORKOUT_EXERCISES.EXERCISE_ID.eq(EXERCISES.ID))
        .join(WORKOUTS)
        .on(WORKOUTS.ID.eq(WORKOUT_EXERCISES.WORKOUT_ID))
        .where(WORKOUTS.USER_ID.eq(me))
        .groupBy(EXERCISES.ID)
        .orderBy(ExerciseRepository.listOrder())
        .fetch(record -> new UsedExercise(record.into(EXERCISES), record.get(last)));
  }

  /**
   * 実施日ごとの値（実施日の古い順）。記録方式に関係なく、すべての値を返す（使う値は {@code TrendRules.point} が選ぶ）。
   *
   * <p>記録の ID は、その日の一番よい値を出した記録。重量 ×
   * 回数は最大重量（1回以上のセット。同じなら回数が多い方）、回数のみは最大回数、時間・距離は最長距離（距離がなければ最長時間）。 それも同じなら、あとに投稿した記録
   *
   * @param recordType 種目の記録方式（weight_reps・reps・time_distance）。一番よい値の決め方に使う
   * @param from 期間の始まりの日（この日を含む）。null なら全期間
   */
  public List<DayAggregate> days(long me, long exerciseId, String recordType, LocalDate from) {
    List<SortField<?>> order = new ArrayList<>(bestFirst(recordType));
    order.add(WORKOUTS.CREATED_AT.desc());
    order.add(WORKOUT_SETS.ID.desc());
    Field<Integer> rank =
        rowNumber().over(partitionBy(WORKOUTS.PERFORMED_ON).orderBy(order)).as("rank");
    Field<BigDecimal> e1rm = E1RM.as("e1rm");
    Table<?> sets =
        select(
                WORKOUTS.ID,
                WORKOUTS.PERFORMED_ON,
                WORKOUT_SETS.WEIGHT_KG,
                WORKOUT_SETS.REPS,
                WORKOUT_SETS.DURATION_SEC,
                WORKOUT_SETS.DISTANCE_KM,
                e1rm,
                rank)
            .from(WORKOUTS)
            .join(WORKOUT_EXERCISES)
            .on(WORKOUT_EXERCISES.WORKOUT_ID.eq(WORKOUTS.ID))
            .join(WORKOUT_SETS)
            .on(WORKOUT_SETS.WORKOUT_EXERCISE_ID.eq(WORKOUT_EXERCISES.ID))
            .where(WORKOUTS.USER_ID.eq(me))
            .and(WORKOUT_EXERCISES.EXERCISE_ID.eq(exerciseId))
            .and(from == null ? noCondition() : WORKOUTS.PERFORMED_ON.ge(from))
            .asTable("sets");
    Field<LocalDate> date = sets.field(WORKOUTS.PERFORMED_ON);
    Field<BigDecimal> weight = sets.field(WORKOUT_SETS.WEIGHT_KG);
    Field<Integer> reps = sets.field(WORKOUT_SETS.REPS);
    Field<Integer> duration = sets.field(WORKOUT_SETS.DURATION_SEC);
    Field<BigDecimal> distance = sets.field(WORKOUT_SETS.DISTANCE_KM);
    Condition top = sets.field(rank).eq(1);

    return dsl.select(
            date,
            max(sets.field(WORKOUTS.ID)).filterWhere(top),
            max(weight).filterWhere(weight.isNotNull().and(reps.ge(1))),
            max(sets.field(e1rm)).filterWhere(reps.between(1, 10)),
            max(reps).filterWhere(top.and(weight.isNotNull()).and(reps.ge(1))),
            sum(reps).cast(SQLDataType.INTEGER),
            max(reps),
            sum(duration).cast(SQLDataType.INTEGER),
            sum(distance),
            coalesce(sum(duration).filterWhere(distance.isNotNull()), inline(BigDecimal.ZERO))
                .cast(SQLDataType.INTEGER))
        .from(sets)
        .groupBy(date)
        .orderBy(date)
        .fetch(Records.mapping(DayAggregate::new));
  }

  /** 全期間の自己ベスト：1回以上のセットの最大重量。同じなら最初に挙げた記録（実施日・投稿日時・セットの順） */
  public Optional<BestWeight> personalBest(long me, long exerciseId) {
    return best(me, exerciseId, WORKOUT_SETS.WEIGHT_KG, LIFTED);
  }

  /** 全期間の推定 1RM の最高：1〜10回のセットから。同じなら最初の記録 */
  public Optional<BestWeight> bestE1rm(long me, long exerciseId) {
    return best(me, exerciseId, E1RM, E1RM_TARGET);
  }

  private Optional<BestWeight> best(
      long me, long exerciseId, Field<BigDecimal> value, Condition target) {
    return dsl.select(value, WORKOUTS.PERFORMED_ON, WORKOUTS.ID)
        .from(WORKOUTS)
        .join(WORKOUT_EXERCISES)
        .on(WORKOUT_EXERCISES.WORKOUT_ID.eq(WORKOUTS.ID))
        .join(WORKOUT_SETS)
        .on(WORKOUT_SETS.WORKOUT_EXERCISE_ID.eq(WORKOUT_EXERCISES.ID))
        .where(WORKOUTS.USER_ID.eq(me))
        .and(WORKOUT_EXERCISES.EXERCISE_ID.eq(exerciseId))
        .and(target)
        .orderBy(
            value.desc(),
            WORKOUTS.PERFORMED_ON.asc(),
            WORKOUTS.CREATED_AT.asc(),
            WORKOUT_SETS.ID.asc())
        .limit(1)
        .fetchOptional(Records.mapping(BestWeight::new));
  }

  /** その日の一番よいセットを先頭にする並び（記録方式ごと） */
  private static List<SortField<?>> bestFirst(String recordType) {
    return switch (recordType) {
      case "weight_reps" ->
          List.of(
              // 0回のセットは、重くても後ろ
              field(WORKOUT_SETS.REPS.ge(1)).desc(),
              WORKOUT_SETS.WEIGHT_KG.desc(),
              WORKOUT_SETS.REPS.desc());
      case "reps" -> List.of(WORKOUT_SETS.REPS.desc());
      default ->
          List.of(WORKOUT_SETS.DISTANCE_KM.desc().nullsLast(), WORKOUT_SETS.DURATION_SEC.desc());
    };
  }
}
