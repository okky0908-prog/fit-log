package com.okimoto.fitlog.notification.service;

/**
 * 通知を作ってほしいというイベント。
 *
 * @param type 種類
 * @param recipientId 受け取る人
 * @param actorId 操作した人。目標の達成は null
 * @param workoutId 対象の投稿。なければ null
 * @param commentId 対象のコメント（コメント・返信の通知は、書かれたコメントそのもの）。なければ null
 */
public record NotificationEvent(
    String type, long recipientId, Long actorId, Long workoutId, Long commentId) {

  /** コメントについての通知でないもの（フォロー・ナイストレーニングなど） */
  public NotificationEvent(String type, long recipientId, Long actorId, Long workoutId) {
    this(type, recipientId, actorId, workoutId, null);
  }
}
