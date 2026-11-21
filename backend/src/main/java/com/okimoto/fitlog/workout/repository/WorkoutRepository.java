package com.okimoto.fitlog.workout.repository;

import static com.okimoto.fitlog.jooq.Tables.EXERCISES;
import static com.okimoto.fitlog.jooq.Tables.USERS;
import static com.okimoto.fitlog.jooq.Tables.WORKOUTS;
import static com.okimoto.fitlog.jooq.Tables.WORKOUT_EXERCISES;
import static com.okimoto.fitlog.jooq.Tables.WORKOUT_SETS;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.select;

import com.okimoto.fitlog.workout.calc.StoredSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Records;
import org.springframework.stereotype.Repository;

/** 記録（workouts・workout_exercises・workout_sets）の読み書き */
@Repository
public class WorkoutRepository {

  private final DSLContext dsl;

  public WorkoutRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * 記録を、種目とセットと一緒に保存する。SQL は種目・セットの数によらず3回（記録・種目・セット）。
   *
   * @return 保存した記録の ID
   */
  public long insert(NewWorkout workout) {
    long workoutId =
        dsl.insertInto(WORKOUTS)
            .set(WORKOUTS.USER_ID, workout.userId())
            .set(WORKOUTS.PERFORMED_ON, workout.performedOn())
            .set(WORKOUTS.BODY, workout.body())
            .set(WORKOUTS.DURATION_MIN, toShort(workout.durationMin()))
            .set(WORKOUTS.VISIBILITY, workout.visibility())
            .set(WORKOUTS.CREATED_AT, workout.createdAt())
            .set(WORKOUTS.UPDATED_AT, workout.createdAt())
            .returning(WORKOUTS.ID)
            .fetchSingle()
            .getId();
    insertExercises(workoutId, workout.exercises());
    return workoutId;
  }

  /**
   * 編集の前に、見てよい記録（{@link WorkoutVisibility}）の投稿者を取る。記録の行をロックして、確かめてから書き込むまでの間に、同時の編集・削除で変わらないようにする。
   *
   * @return 投稿者の ID。見てはいけない・ない記録は空
   */
  public Optional<Long> lockVisible(long id, long me) {
    return dsl.select(WORKOUTS.USER_ID)
        .from(WORKOUTS)
        .join(USERS)
        .on(USERS.ID.eq(WORKOUTS.USER_ID))
        .where(WORKOUTS.ID.eq(id))
        .and(WorkoutVisibility.visibleTo(me))
        .forUpdate()
        .of(WORKOUTS)
        .fetchOptional(WORKOUTS.USER_ID);
  }

  /** 記録を編集する。種目とセットは、前の行を消して入れ直す（セットは外部キーの CASCADE で一緒に消える） */
  public void update(long id, WorkoutEdit edit) {
    dsl.update(WORKOUTS)
        .set(WORKOUTS.PERFORMED_ON, edit.performedOn())
        .set(WORKOUTS.BODY, edit.body())
        .set(WORKOUTS.DURATION_MIN, toShort(edit.durationMin()))
        .set(WORKOUTS.VISIBILITY, edit.visibility())
        .set(WORKOUTS.EDITED_AT, edit.editedAt())
        .set(WORKOUTS.UPDATED_AT, edit.editedAt())
        .where(WORKOUTS.ID.eq(id))
        .execute();
    dsl.deleteFrom(WORKOUT_EXERCISES).where(WORKOUT_EXERCISES.WORKOUT_ID.eq(id)).execute();
    insertExercises(id, edit.exercises());
  }

  /** 記録を消す。種目・セットは外部キーの CASCADE で一緒に消える */
  public void delete(long id) {
    dsl.deleteFrom(WORKOUTS).where(WORKOUTS.ID.eq(id)).execute();
  }

  /** 種目とセットを入れる。SQL は数によらず2回（種目・セット） */
  private void insertExercises(long workoutId, List<NewWorkout.Exercise> workoutExercises) {
    var exercises =
        dsl.insertInto(
            WORKOUT_EXERCISES,
            WORKOUT_EXERCISES.WORKOUT_ID,
            WORKOUT_EXERCISES.EXERCISE_ID,
            WORKOUT_EXERCISES.SORT_ORDER);
    for (int i = 0; i < workoutExercises.size(); i++) {
      exercises =
          exercises.values(workoutId, workoutExercises.get(i).exerciseId(), (short) (i + 1));
    }
    // RETURNING の行の順番は決まっていないので、並び順で ID を引けるようにする
    Map<Short, Long> idBySortOrder =
        exercises
            .returning(WORKOUT_EXERCISES.ID, WORKOUT_EXERCISES.SORT_ORDER)
            .fetchMap(WORKOUT_EXERCISES.SORT_ORDER, WORKOUT_EXERCISES.ID);

    var sets =
        dsl.insertInto(
            WORKOUT_SETS,
            WORKOUT_SETS.WORKOUT_EXERCISE_ID,
            WORKOUT_SETS.SORT_ORDER,
            WORKOUT_SETS.WEIGHT_KG,
            WORKOUT_SETS.REPS,
            WORKOUT_SETS.DURATION_SEC,
            WORKOUT_SETS.DISTANCE_KM);
    for (int i = 0; i < workoutExercises.size(); i++) {
      long workoutExerciseId = idBySortOrder.get((short) (i + 1));
      List<StoredSet> exerciseSets = workoutExercises.get(i).sets();
      for (int j = 0; j < exerciseSets.size(); j++) {
        StoredSet set = exerciseSets.get(j);
        sets =
            sets.values(
                workoutExerciseId,
                (short) (j + 1),
                set.weightKg(),
                set.reps(),
                set.durationSec(),
                set.distanceKm());
      }
    }
    sets.execute();
  }

  private static Short toShort(Integer value) {
    return value == null ? null : value.shortValue();
  }

  /** 見てよい記録の詳細（{@link WorkoutVisibility}）。見てはいけない・ない記録は空。種目とセットも含めて1回の SQL で取る */
  public Optional<WorkoutDetail> findVisible(long id, long me) {
    return dsl.select(
            WORKOUTS.ID,
            WORKOUTS.USER_ID,
            USERS.USERNAME,
            USERS.DISPLAY_NAME,
            USERS.IS_PRIVATE,
            WORKOUTS.PERFORMED_ON,
            WORKOUTS.CREATED_AT,
            WORKOUTS.EDITED_AT,
            WORKOUTS.BODY,
            WORKOUTS.DURATION_MIN,
            WORKOUTS.VISIBILITY,
            multiset(
                    select(
                            WORKOUT_EXERCISES.EXERCISE_ID,
                            EXERCISES.NAME,
                            EXERCISES.BODY_PART,
                            EXERCISES.RECORD_TYPE,
                            EXERCISES.IS_RUNNING,
                            multiset(
                                    select(
                                            WORKOUT_SETS.WEIGHT_KG,
                                            WORKOUT_SETS.REPS,
                                            WORKOUT_SETS.DURATION_SEC,
                                            WORKOUT_SETS.DISTANCE_KM)
                                        .from(WORKOUT_SETS)
                                        .where(
                                            WORKOUT_SETS.WORKOUT_EXERCISE_ID.eq(
                                                WORKOUT_EXERCISES.ID))
                                        .orderBy(WORKOUT_SETS.SORT_ORDER))
                                .convertFrom(r -> r.map(Records.mapping(StoredSet::new))))
                        .from(WORKOUT_EXERCISES)
                        .join(EXERCISES)
                        .on(EXERCISES.ID.eq(WORKOUT_EXERCISES.EXERCISE_ID))
                        .where(WORKOUT_EXERCISES.WORKOUT_ID.eq(WORKOUTS.ID))
                        .orderBy(WORKOUT_EXERCISES.SORT_ORDER))
                .convertFrom(r -> r.map(Records.mapping(WorkoutDetail.Exercise::new))))
        .from(WORKOUTS)
        .join(USERS)
        .on(USERS.ID.eq(WORKOUTS.USER_ID))
        .where(WORKOUTS.ID.eq(id))
        .and(WorkoutVisibility.visibleTo(me))
        .fetchOptional(Records.mapping(WorkoutDetail::new));
  }
}
