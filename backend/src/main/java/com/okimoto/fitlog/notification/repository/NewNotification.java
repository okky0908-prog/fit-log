package com.okimoto.fitlog.notification.repository;

import java.time.OffsetDateTime;

/**
 * 新しく保存する通知（docs/database.md「notifications」の種類ごとの列）。
 *
 * @param recipientId 受け取る人
 * @param actorId 操作した人。目標の達成は null
 * @param type 種類（like・comment・reply・follow・follow_request・follow_accepted・goal_achieved・goal_cheer）
 * @param workoutId 対象の投稿。投稿についての通知でなければ null
 * @param createdAt 作った日時
 */
public record NewNotification(
    long recipientId, Long actorId, String type, Long workoutId, OffsetDateTime createdAt) {}
