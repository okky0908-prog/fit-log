package com.okimoto.fitlog.workout.repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 記録の編集の内容。種目とセットは、この内容で置き換える（並び順のとおりに1から番号を振り直す）。
 *
 * @param performedOn 実施日
 * @param body ひとこと（そろえたもの。なければ空）
 * @param durationMin トレーニング時間（分）。なければ null
 * @param visibility 公開範囲
 * @param editedAt 編集した日時（edited_at と updated_at に入れる）
 * @param exercises 種目とセット
 */
public record WorkoutEdit(
    LocalDate performedOn,
    String body,
    Integer durationMin,
    String visibility,
    OffsetDateTime editedAt,
    List<NewWorkout.Exercise> exercises) {}
