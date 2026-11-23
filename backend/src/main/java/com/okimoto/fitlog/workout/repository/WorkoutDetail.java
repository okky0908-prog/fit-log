package com.okimoto.fitlog.workout.repository;

import com.okimoto.fitlog.workout.calc.StoredSet;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 記録の詳細（A-21 の元のデータ）。
 *
 * @param id 記録
 * @param userId 投稿者
 * @param username 投稿者のユーザー名
 * @param displayName 投稿者の表示名
 * @param authorPrivate 投稿者が鍵アカウントか
 * @param authorIconKey 投稿者のアイコンの S3 のキー。なければ null
 * @param performedOn 実施日
 * @param createdAt 投稿日時
 * @param editedAt 編集した日時。未編集なら null
 * @param body ひとこと
 * @param durationMin トレーニング時間（分）。なければ null
 * @param visibility 公開範囲
 * @param exercises 種目とセット（並び順のとおり）
 * @param images 画像（並び順のとおり）
 */
public record WorkoutDetail(
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
    Short durationMin,
    String visibility,
    List<Exercise> exercises,
    List<Image> images) {

  /**
   * 記録の種目。
   *
   * @param exerciseId 種目
   * @param name 種目の名前（他人の自分専用の種目も、名前はそのまま）
   * @param bodyPart 部位
   * @param recordType 記録方式
   * @param isRunning ランニングか
   * @param sets セット（並び順のとおり）
   */
  public record Exercise(
      long exerciseId,
      String name,
      String bodyPart,
      String recordType,
      boolean isRunning,
      List<StoredSet> sets) {}

  /**
   * 記録の画像。
   *
   * @param id 画像（編集で残す画像を指定するときに使う）
   * @param imageKey S3 のキー
   * @param sortOrder 表示順（1〜4）
   */
  public record Image(long id, String imageKey, short sortOrder) {}
}
