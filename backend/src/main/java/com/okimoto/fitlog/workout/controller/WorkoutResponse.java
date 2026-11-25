package com.okimoto.fitlog.workout.controller;

import com.okimoto.fitlog.common.calc.JapanTime;
import com.okimoto.fitlog.workout.calc.StoredSet;
import com.okimoto.fitlog.workout.repository.WorkoutDetail;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;

/**
 * 記録の詳細（docs/api.md「Workout」）。他人にも返す形なので、項目を足すときは本人以外に出してよいかを考える（docs/test-plan.md 4.2
 * の許可リストのテストが落ちる）。
 *
 * <p>まだ作っていない機能の項目は、決まった値を返す：{@code personalBest} は false（段階 2）
 *
 * @param id 記録
 * @param author 投稿者
 * @param performedOn 実施日
 * @param createdAt 投稿日時（日本時間）
 * @param editedAt 編集した日時（日本時間）。未編集なら null
 * @param body ひとこと
 * @param durationMin トレーニング時間（分）。なければ null
 * @param visibility 公開範囲
 * @param exercises 種目とセット（並び順のとおり）
 * @param images 画像
 * @param likeCount ナイストレーニングの数
 * @param likedByMe 自分が付けたか
 * @param commentCount コメントの数（返信を含む）
 * @param mine 自分の記録か
 */
public record WorkoutResponse(
    long id,
    Author author,
    LocalDate performedOn,
    OffsetDateTime createdAt,
    OffsetDateTime editedAt,
    String body,
    Integer durationMin,
    String visibility,
    List<Exercise> exercises,
    List<Image> images,
    int likeCount,
    boolean likedByMe,
    int commentCount,
    boolean mine) {

  /** 投稿者 */
  public record Author(String username, String displayName, String iconUrl, boolean isPrivate) {}

  /** 記録の種目 */
  public record Exercise(
      long exerciseId,
      String name,
      String bodyPart,
      String recordType,
      boolean isRunning,
      boolean personalBest,
      List<Set> sets) {}

  /** セット（kg・km・秒） */
  public record Set(
      BigDecimal weightKg,
      Integer reps,
      Integer durationSec,
      BigDecimal distanceKm,
      boolean personalBest) {}

  /** 画像 */
  public record Image(long id, String url, int sortOrder) {}

  /**
   * @param urlOf 画像の S3 のキーから、署名付き URL を作る（閲覧を許可したあとに呼ぶ）
   * @param iconUrlOf アイコンの S3 のキーから、署名付き URL を作る（なければ null）
   */
  static WorkoutResponse from(
      WorkoutDetail detail,
      long me,
      Function<String, String> urlOf,
      Function<String, String> iconUrlOf) {
    return new WorkoutResponse(
        detail.id(),
        new Author(
            detail.username(),
            detail.displayName(),
            iconUrlOf.apply(detail.authorIconKey()),
            detail.authorPrivate()),
        detail.performedOn(),
        JapanTime.of(detail.createdAt()),
        JapanTime.of(detail.editedAt()),
        detail.body(),
        detail.durationMin() == null ? null : detail.durationMin().intValue(),
        detail.visibility(),
        detail.exercises().stream().map(WorkoutResponse::exercise).toList(),
        detail.images().stream()
            .map(image -> new Image(image.id(), urlOf.apply(image.imageKey()), image.sortOrder()))
            .toList(),
        detail.likeCount(),
        detail.likedByMe(),
        detail.commentCount(),
        detail.userId() == me);
  }

  private static Exercise exercise(WorkoutDetail.Exercise exercise) {
    return new Exercise(
        exercise.exerciseId(),
        exercise.name(),
        exercise.bodyPart(),
        exercise.recordType(),
        exercise.isRunning(),
        false,
        exercise.sets().stream().map(WorkoutResponse::set).toList());
  }

  private static Set set(StoredSet set) {
    return new Set(set.weightKg(), set.reps(), set.durationSec(), set.distanceKm(), false);
  }
}
