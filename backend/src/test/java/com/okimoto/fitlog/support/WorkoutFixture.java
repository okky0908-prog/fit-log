package com.okimoto.fitlog.support;

import static com.okimoto.fitlog.jooq.Tables.EXERCISES;

import com.okimoto.fitlog.workout.calc.StoredSet;
import com.okimoto.fitlog.workout.repository.NewWorkout;
import com.okimoto.fitlog.workout.repository.WorkoutRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jooq.DSLContext;

/**
 * テスト用の記録の組み立て役（docs/test-plan.md 7章）。テストの中では、そのケースで大事な値だけを書く。
 *
 * <pre>{@code
 * long id = fixture.of(userId).visibility("private").weightReps("ベンチプレス", 60, 10, 3).save();
 * }</pre>
 *
 * 種目は最初から用意された種目の名前で指定する。種目を1つも足さなければ、腕立て伏せ 20回 × 1セットにする
 */
public final class WorkoutFixture {

  private final DSLContext dsl;
  private final WorkoutRepository workoutRepository;

  public WorkoutFixture(DSLContext dsl, WorkoutRepository workoutRepository) {
    this.dsl = dsl;
    this.workoutRepository = workoutRepository;
  }

  public Builder of(long userId) {
    return new Builder(userId);
  }

  public final class Builder {

    private final long userId;
    private LocalDate performedOn = LocalDate.of(2026, 11, 20);
    private String body = "";
    private Integer durationMin;
    private String visibility = "public";
    private OffsetDateTime createdAt = OffsetDateTime.parse("2026-11-20T19:00:00+09:00");
    private final List<NewWorkout.Exercise> exercises = new ArrayList<>();

    private Builder(long userId) {
      this.userId = userId;
    }

    public Builder performedOn(LocalDate value) {
      this.performedOn = value;
      return this;
    }

    public Builder body(String value) {
      this.body = value;
      return this;
    }

    public Builder durationMin(int value) {
      this.durationMin = value;
      return this;
    }

    public Builder visibility(String value) {
      this.visibility = value;
      return this;
    }

    public Builder createdAt(OffsetDateTime value) {
      this.createdAt = value;
      return this;
    }

    /** 重量 × 回数の種目を、同じ値のセットで足す */
    public Builder weightReps(String exerciseName, double weightKg, int reps, int setCount) {
      StoredSet set = new StoredSet(BigDecimal.valueOf(weightKg).setScale(3), reps, null, null);
      return exercise(exerciseName, Collections.nCopies(setCount, set));
    }

    /** 回数のみの種目を、同じ値のセットで足す */
    public Builder reps(String exerciseName, int reps, int setCount) {
      return exercise(
          exerciseName, Collections.nCopies(setCount, new StoredSet(null, reps, null, null)));
    }

    /** 時間・距離の種目を1セット足す。距離がなければ null */
    public Builder timeDistance(String exerciseName, int durationSec, Double distanceKm) {
      BigDecimal distance = distanceKm == null ? null : BigDecimal.valueOf(distanceKm).setScale(3);
      return exercise(exerciseName, List.of(new StoredSet(null, null, durationSec, distance)));
    }

    public Builder exercise(String exerciseName, List<StoredSet> sets) {
      long exerciseId =
          dsl.select(EXERCISES.ID)
              .from(EXERCISES)
              .where(EXERCISES.NAME.eq(exerciseName))
              .and(EXERCISES.OWNER_ID.isNull())
              .fetchSingle()
              .value1();
      exercises.add(new NewWorkout.Exercise(exerciseId, sets));
      return this;
    }

    /** 保存して、記録の ID を返す */
    public long save() {
      if (exercises.isEmpty()) {
        reps("腕立て伏せ", 20, 1);
      }
      return workoutRepository.insert(
          new NewWorkout(userId, performedOn, body, durationMin, visibility, createdAt, exercises));
    }
  }
}
