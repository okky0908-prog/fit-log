package com.okimoto.fitlog.exercise.controller;

import com.okimoto.fitlog.jooq.tables.records.ExercisesRecord;

/**
 * 種目（docs/api.md A-30 の各要素）
 *
 * @param custom 自分専用の種目か
 */
public record ExerciseResponse(
    long id, String name, String bodyPart, String recordType, boolean isRunning, boolean custom) {

  public static ExerciseResponse from(ExercisesRecord exercise) {
    return new ExerciseResponse(
        exercise.getId(),
        exercise.getName(),
        exercise.getBodyPart(),
        exercise.getRecordType(),
        exercise.getIsRunning(),
        exercise.getOwnerId() != null);
  }
}
