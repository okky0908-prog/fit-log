package com.okimoto.fitlog.workout.service;

import com.okimoto.fitlog.common.calc.JapanTime;
import com.okimoto.fitlog.common.controller.ApiError.FieldError;
import com.okimoto.fitlog.exercise.repository.ExerciseRepository;
import com.okimoto.fitlog.workout.calc.SetInput;
import com.okimoto.fitlog.workout.calc.SetRules;
import com.okimoto.fitlog.workout.calc.WorkoutRules;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 記録の入力チェック（docs/features.md「記録の項目」、docs/test-plan.md 5.1・5.2）。違反を1つで止めず、すべての項目のエラーを集める。
 *
 * <p>エラーの項目の場所は {@code exercises[1].sets[0].weight} の形（docs/api.md「エラー」）
 */
@Component
public class WorkoutInputValidator {

  static final int MAX_EXERCISES = 20;
  static final int MAX_SETS = 20;

  private static final Set<String> VISIBILITIES = Set.of("public", "followers", "private");
  private static final Set<String> WEIGHT_UNITS = Set.of("kg", "lb");
  private static final Set<String> DISTANCE_UNITS = Set.of("km", "mi");

  private final ExerciseRepository exerciseRepository;
  private final Clock clock;

  public WorkoutInputValidator(ExerciseRepository exerciseRepository, Clock clock) {
    this.exerciseRepository = exerciseRepository;
    this.clock = clock;
  }

  /**
   * @param me 記録する人（その人の自分専用の種目だけを使える）
   * @return 項目ごとのエラー。なければ空
   */
  public List<FieldError> validate(long me, WorkoutInput input) {
    List<FieldError> errors = new ArrayList<>();
    if (input.performedOn() == null) {
      errors.add(new FieldError("performedOn", "実施日を入力してください"));
    } else if (input.performedOn().isAfter(JapanTime.today(clock))) {
      errors.add(new FieldError("performedOn", "未来の日付は選べません"));
    }
    if (WorkoutRules.bodyTooLong(WorkoutRules.normalizeBody(input.body()))) {
      errors.add(new FieldError("body", "ひとことは280文字以内で入力してください"));
    }
    if (input.durationMin() != null && !integerBetween(input.durationMin(), 1, 1440)) {
      errors.add(new FieldError("durationMin", "トレーニング時間は1〜1440分で入力してください"));
    }
    if (!isOneOf(VISIBILITIES, input.visibility())) {
      errors.add(new FieldError("visibility", "公開範囲を選んでください"));
    }
    boolean unitsValid = true;
    if (!isOneOf(WEIGHT_UNITS, input.weightUnit())) {
      errors.add(new FieldError("weightUnit", "重量の単位を選んでください"));
      unitsValid = false;
    }
    if (!isOneOf(DISTANCE_UNITS, input.distanceUnit())) {
      errors.add(new FieldError("distanceUnit", "距離の単位を選んでください"));
      unitsValid = false;
    }
    validateExercises(me, input, unitsValid, errors);
    return errors;
  }

  private void validateExercises(
      long me, WorkoutInput input, boolean unitsValid, List<FieldError> errors) {
    List<WorkoutInput.Exercise> exercises = input.exercises();
    if (exercises == null || exercises.isEmpty() || exercises.size() > MAX_EXERCISES) {
      errors.add(new FieldError("exercises", "種目は1〜20件で入力してください"));
      if (exercises == null) {
        return;
      }
    }
    Map<Long, String> recordTypes =
        exerciseRepository.findUsable(
            me,
            exercises.stream()
                .filter(Objects::nonNull)
                .map(WorkoutInput.Exercise::exerciseId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
    for (int i = 0; i < exercises.size(); i++) {
      String path = "exercises[" + i + "]";
      WorkoutInput.Exercise exercise = exercises.get(i);
      if (exercise == null) {
        errors.add(new FieldError(path, "種目を入力してください"));
        continue;
      }
      // 他人の自分専用の種目も「ない種目」と同じエラーにする（他人の種目があると分からないように）
      String recordType =
          exercise.exerciseId() == null ? null : recordTypes.get(exercise.exerciseId());
      if (recordType == null) {
        errors.add(new FieldError(path + ".exerciseId", "種目が見つかりません"));
      }
      validateSets(exercise.sets(), recordType, input, unitsValid, path, errors);
    }
  }

  /** 種目が使えないとき・単位が正しくないときは、セットの値は確かめない（どの決まりで確かめるか決まらないため） */
  private static void validateSets(
      List<SetInput> sets,
      String recordType,
      WorkoutInput input,
      boolean unitsValid,
      String path,
      List<FieldError> errors) {
    if (sets == null || sets.isEmpty() || sets.size() > MAX_SETS) {
      errors.add(new FieldError(path + ".sets", "セットは1〜20件で入力してください"));
      if (sets == null) {
        return;
      }
    }
    for (int j = 0; j < sets.size(); j++) {
      String setPath = path + ".sets[" + j + "]";
      SetInput set = sets.get(j);
      if (set == null) {
        errors.add(new FieldError(setPath, "セットを入力してください"));
      } else if (recordType != null && unitsValid) {
        SetRules.check(set, recordType, input.weightUnit(), input.distanceUnit())
            .forEach(
                (field, message) -> errors.add(new FieldError(setPath + "." + field, message)));
      }
    }
  }

  /** Set.of の contains は null で例外を投げるので、null を先に見る */
  private static boolean isOneOf(Set<String> values, String value) {
    return value != null && values.contains(value);
  }

  private static boolean integerBetween(BigDecimal value, int min, int max) {
    return value.stripTrailingZeros().scale() <= 0
        && value.compareTo(BigDecimal.valueOf(min)) >= 0
        && value.compareTo(BigDecimal.valueOf(max)) <= 0;
  }
}
