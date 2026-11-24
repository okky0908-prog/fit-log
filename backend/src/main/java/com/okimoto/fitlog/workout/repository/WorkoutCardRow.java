package com.okimoto.fitlog.workout.repository;

import com.okimoto.fitlog.workout.calc.ExerciseAggregate;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 記録の一覧の1件（docs/api.md「WorkoutCard」のもとになる値）。種目はセットを集計した値だけを持つ。
 *
 * @param id 記録
 * @param userId 投稿者の ID
 * @param username 投稿者のユーザー名
 * @param displayName 投稿者の表示名
 * @param authorPrivate 投稿者が鍵アカウントか
 * @param authorIconKey 投稿者のアイコンの S3 のキー。なければ null
 * @param performedOn 実施日
 * @param createdAt 投稿日時
 * @param editedAt 編集した日時。未編集なら null
 * @param body ひとこと
 * @param visibility 公開範囲
 * @param exercises 先頭5種目の集計（並び順のとおり）
 * @param otherExerciseCount 6種目目からの種目の数
 * @param images 画像（並び順のとおり）
 */
public record WorkoutCardRow(
    long id,
    long userId,
    String username,
    String displayName,
    boolean authorPrivate,
    String authorIconKey,
    LocalDate performedOn,
    OffsetDateTime createdAt,
    OffsetDateTime editedAt,
    String body,
    String visibility,
    List<ExerciseAggregate> exercises,
    int otherExerciseCount,
    List<WorkoutDetail.Image> images) {}
