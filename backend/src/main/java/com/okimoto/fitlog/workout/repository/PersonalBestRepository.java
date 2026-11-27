package com.okimoto.fitlog.workout.repository;

import static com.okimoto.fitlog.jooq.Tables.WORKOUTS;
import static com.okimoto.fitlog.jooq.Tables.WORKOUT_EXERCISES;
import static com.okimoto.fitlog.jooq.Tables.WORKOUT_SETS;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.row;
import static org.jooq.impl.DSL.select;

import com.okimoto.fitlog.jooq.tables.WorkoutExercises;
import com.okimoto.fitlog.jooq.tables.WorkoutSets;
import com.okimoto.fitlog.jooq.tables.Workouts;
import com.okimoto.fitlog.workout.calc.PersonalBest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Records;
import org.jooq.Table;
import org.springframework.stereotype.Repository;

/**
 * 自己ベスト更新の印（F-85、docs/database.md「自己ベストと『自己ベスト更新』の印」）。
 *
 * <p>表示する記録（1ページ分）について、1回の SQL で求める。公開範囲に関係なく、その人のすべての記録と比べる（印から非公開の記録の値は分からない）。 呼ぶ側は、見てよい記録の ID
 * だけを渡す
 */
@Repository
public class PersonalBestRepository {

  /** 表示する記録・その種目・セット */
  private static final Workouts W = WORKOUTS.as("w");

  private static final WorkoutExercises WE = WORKOUT_EXERCISES.as("we");
  private static final WorkoutSets S = WORKOUT_SETS.as("s");

  /** 比べる前の記録・その種目・セット */
  private static final Workouts PW = WORKOUTS.as("pw");

  private static final WorkoutExercises PWE = WORKOUT_EXERCISES.as("pwe");
  private static final WorkoutSets PS = WORKOUT_SETS.as("ps");

  private final DSLContext dsl;

  public PersonalBestRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * 記録のうち、自己ベストを更新した種目。種目ごとの最大重量（回数が1回以上のセット）が、それより前（実施日・投稿日時・ID の順）の、同じ人の同じ種目の最大重量を超えたもの。
   * その種目を初めて記録したもの（前の記録がない）は入らない。同じ記録に同じ種目が2回あれば、まとめて1件にする。
   *
   * @param workoutIds 表示する記録
   */
  public List<PersonalBest> findUpdated(Collection<Long> workoutIds) {
    if (workoutIds.isEmpty()) {
      return List.of();
    }
    Field<BigDecimal> maxWeight = max(S.WEIGHT_KG).as("max_weight");
    Table<?> current =
        select(W.ID, W.USER_ID, W.PERFORMED_ON, W.CREATED_AT, WE.EXERCISE_ID, maxWeight)
            .from(W)
            .join(WE)
            .on(WE.WORKOUT_ID.eq(W.ID))
            .join(S)
            .on(S.WORKOUT_EXERCISE_ID.eq(WE.ID))
            .where(W.ID.in(workoutIds))
            .and(S.WEIGHT_KG.isNotNull())
            .and(S.REPS.ge(1))
            .groupBy(W.ID, W.USER_ID, W.PERFORMED_ON, W.CREATED_AT, WE.EXERCISE_ID)
            .asTable("t");
    Field<Long> workoutId = current.field(W.ID);
    Field<Long> userId = current.field(W.USER_ID);
    Field<LocalDate> performedOn = current.field(W.PERFORMED_ON);
    Field<OffsetDateTime> createdAt = current.field(W.CREATED_AT);
    Field<Long> exerciseId = current.field(WE.EXERCISE_ID);
    Field<BigDecimal> currentMax = current.field(maxWeight);

    // 前の記録までの自己ベスト。前の記録がなければ NULL になり、下の比較で外れる
    Field<BigDecimal> previousBest =
        field(
            select(max(PS.WEIGHT_KG))
                .from(PW)
                .join(PWE)
                .on(PWE.WORKOUT_ID.eq(PW.ID))
                .join(PS)
                .on(PS.WORKOUT_EXERCISE_ID.eq(PWE.ID))
                .where(PW.USER_ID.eq(userId))
                .and(PWE.EXERCISE_ID.eq(exerciseId))
                .and(PS.WEIGHT_KG.isNotNull())
                .and(PS.REPS.ge(1))
                .and(
                    row(PW.PERFORMED_ON, PW.CREATED_AT, PW.ID)
                        .lt(performedOn, createdAt, workoutId)));

    return dsl.select(workoutId, exerciseId, currentMax)
        .from(current)
        .where(currentMax.gt(previousBest))
        .fetch(Records.mapping(PersonalBest::new));
  }
}
