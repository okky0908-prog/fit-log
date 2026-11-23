package com.okimoto.fitlog.notification.repository;

import java.time.OffsetDateTime;

/**
 * 通知一覧の1件（A-120 の元のデータ）。
 *
 * @param id 通知
 * @param type 種類
 * @param createdAt 作った日時
 * @param readAt 既読にした日時。未読なら null
 * @param actorUsername 操作した人のユーザー名。目標の達成など、操作した人がいなければ null
 * @param actorDisplayName 操作した人の表示名
 * @param actorPrivate 操作した人が鍵アカウントか
 * @param actorIconKey 操作した人のアイコンの S3 のキー。なければ null
 * @param workoutId 対象の投稿。なければ null
 * @param workoutBody 対象の投稿のひとこと（抜き出す前）
 */
public record NotificationRow(
    long id,
    String type,
    OffsetDateTime createdAt,
    OffsetDateTime readAt,
    String actorUsername,
    String actorDisplayName,
    Boolean actorPrivate,
    String actorIconKey,
    Long workoutId,
    String workoutBody) {}
