package com.okimoto.fitlog.workout.repository;

import com.okimoto.fitlog.workout.calc.StoredSet;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 新しく保存する記録。種目とセットは並び順のとおり（1から番号を振る）。
 *
 * @param userId 投稿者
 * @param performedOn 実施日
 * @param body ひとこと（そろえたもの。なければ空）
 * @param durationMin トレーニング時間（分）。なければ null
 * @param visibility 公開範囲
 * @param createdAt 投稿日時
 * @param exercises 種目とセット
 */
public record NewWorkout(
    long userId,
    LocalDate performedOn,
    String body,
    Integer durationMin,
    String visibility,
    OffsetDateTime createdAt,
    List<Exercise> exercises) {

  /**
   * 記録の種目。
   *
   * @param exerciseId 種目
   * @param sets セット（kg・km・秒）
   */
  public record Exercise(long exerciseId, List<StoredSet> sets) {}
}
