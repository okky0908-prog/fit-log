package com.okimoto.fitlog.workout.controller;

import com.okimoto.fitlog.common.calc.JapanTime;
import com.okimoto.fitlog.workout.calc.ExerciseSummary;
import com.okimoto.fitlog.workout.calc.ExerciseSummaryRules;
import com.okimoto.fitlog.workout.repository.WorkoutCardRow;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;

/**
 * 記録の一覧の1件（docs/api.md「WorkoutCard」。A-50・A-51・A-81、のちに A-54・A-93）。投稿カードに出すものだけを返す。
 * 他人にも返す形なので、項目を足すときは本人以外に出してよいかを考える。
 *
 * <p>まだ作っていない機能の項目は、決まった値を返す：種目の {@code personalBest} は false（段階 2）
 *
 * @param id 記録
 * @param author 投稿者
 * @param performedOn 実施日
 * @param createdAt 投稿日時（日本時間）
 * @param editedAt 編集した日時（日本時間）。未編集なら null
 * @param body ひとこと
 * @param visibility 公開範囲
 * @param exerciseSummaries 先頭5種目の要約
 * @param otherExerciseCount 6種目目からの種目の数（「ほか N 種目」）
 * @param images 画像
 * @param likeCount ナイストレーニングの数
 * @param likedByMe 自分が付けたか
 * @param commentCount コメントの数（返信を含む）
 * @param mine 自分の記録か
 */
public record WorkoutCardResponse(
    long id,
    WorkoutResponse.Author author,
    LocalDate performedOn,
    OffsetDateTime createdAt,
    OffsetDateTime editedAt,
    String body,
    String visibility,
    List<ExerciseSummary> exerciseSummaries,
    int otherExerciseCount,
    List<WorkoutResponse.Image> images,
    int likeCount,
    boolean likedByMe,
    int commentCount,
    boolean mine) {

  /**
   * @param urlOf 画像の S3 のキーから、署名付き URL を作る（閲覧の条件で絞った一覧にだけ使う）
   * @param iconUrlOf アイコンの S3 のキーから、署名付き URL を作る（なければ null）
   */
  public static WorkoutCardResponse from(
      WorkoutCardRow row,
      long me,
      Function<String, String> urlOf,
      Function<String, String> iconUrlOf) {
    return new WorkoutCardResponse(
        row.id(),
        new WorkoutResponse.Author(
            row.username(),
            row.displayName(),
            iconUrlOf.apply(row.authorIconKey()),
            row.authorPrivate()),
        row.performedOn(),
        JapanTime.of(row.createdAt()),
        JapanTime.of(row.editedAt()),
        row.body(),
        row.visibility(),
        row.exercises().stream().map(ExerciseSummaryRules::summarize).toList(),
        row.otherExerciseCount(),
        row.images().stream()
            .map(image ->
                new WorkoutResponse.Image(
                    image.id(), urlOf.apply(image.imageKey()), image.sortOrder()))
            .toList(),
        row.likeCount(),
        row.likedByMe(),
        row.commentCount(),
        row.userId() == me);
  }
}
