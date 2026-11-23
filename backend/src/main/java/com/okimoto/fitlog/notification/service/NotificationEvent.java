package com.okimoto.fitlog.notification.service;

/**
 * 通知を作ってほしいというイベント。
 *
 * @param type 種類
 * @param recipientId 受け取る人
 * @param actorId 操作した人。目標の達成は null
 * @param workoutId 対象の投稿。なければ null
 */
public record NotificationEvent(String type, long recipientId, Long actorId, Long workoutId) {}
