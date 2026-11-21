package com.okimoto.fitlog.workout.service;

import com.okimoto.fitlog.common.controller.ApiError.FieldError;
import com.okimoto.fitlog.common.controller.ApiException;
import com.okimoto.fitlog.common.controller.ErrorCode;
import com.okimoto.fitlog.workout.calc.SetRules;
import com.okimoto.fitlog.workout.calc.WorkoutRules;
import com.okimoto.fitlog.workout.repository.NewWorkout;
import com.okimoto.fitlog.workout.repository.WorkoutDetail;
import com.okimoto.fitlog.workout.repository.WorkoutRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 記録の作成と詳細（F-10・F-13、docs/api.md A-20・A-21） */
@Service
public class WorkoutService {

  private final WorkoutRepository workoutRepository;
  private final WorkoutInputValidator validator;
  private final Clock clock;

  public WorkoutService(
      WorkoutRepository workoutRepository, WorkoutInputValidator validator, Clock clock) {
    this.workoutRepository = workoutRepository;
    this.validator = validator;
    this.clock = clock;
  }

  /**
   * A-20 記録の作成。入力のエラーはまとめて 400 で返す。
   *
   * <p>画像（8本目）・ハッシュタグの保存（段階 4）・確定後の目標の判定（段階 3）は、それぞれを作るときに足す
   *
   * @return 作った記録の詳細
   */
  @Transactional
  public WorkoutDetail create(long me, WorkoutInput input) {
    List<FieldError> errors = validator.validate(me, input);
    if (!errors.isEmpty()) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, errors);
    }
    long id =
        workoutRepository.insert(
            new NewWorkout(
                me,
                input.performedOn(),
                WorkoutRules.normalizeBody(input.body()),
                input.durationMin() == null ? null : input.durationMin().intValueExact(),
                input.visibility(),
                OffsetDateTime.now(clock),
                input.exercises().stream()
                    .map(exercise ->
                        new NewWorkout.Exercise(
                            exercise.exerciseId(),
                            exercise.sets().stream()
                                .map(set ->
                                    SetRules.toStored(
                                        set, input.weightUnit(), input.distanceUnit()))
                                .toList()))
                    .toList()));
    return find(me, id);
  }

  /** A-21 記録の詳細。見てはいけない記録は、ない記録と同じ 404 */
  @Transactional(readOnly = true)
  public WorkoutDetail find(long me, long id) {
    return workoutRepository
        .findVisible(id, me)
        .orElseThrow(() -> new ApiException(ErrorCode.WORKOUT_NOT_FOUND));
  }
}
