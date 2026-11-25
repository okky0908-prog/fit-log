package com.okimoto.fitlog.notification.controller;

import com.okimoto.fitlog.common.calc.Excerpt;
import com.okimoto.fitlog.common.calc.JapanTime;
import com.okimoto.fitlog.notification.repository.NotificationRow;
import java.time.OffsetDateTime;
import java.util.function.Function;

/**
 * 通知（docs/api.md「Notification」）。文言（「〇〇さんがあなたの記録にコメントしました」）は画面が type から作る。
 *
 * <p>目標（段階 3）は、目標の表を作るまで null を返す
 *
 * @param id 通知
 * @param type 種類
 * @param createdAt 作った日時（日本時間）
 * @param read 既読か
 * @param actor 操作した人。目標の達成など、操作した人がいなければ null
 * @param workout 対象の投稿。なければ null
 * @param comment 対象のコメント
 * @param goal 対象の目標
 */
public record NotificationResponse(
    long id,
    String type,
    OffsetDateTime createdAt,
    boolean read,
    Actor actor,
    Target workout,
    Target comment,
    Target goal) {

  /** 本文の抜き出しの文字数 */
  static final int EXCERPT_LENGTH = 30;

  /** 操作した人（docs/api.md「UserSummary」） */
  public record Actor(String username, String displayName, String iconUrl, boolean isPrivate) {}

  /** 対象（投稿・コメント）。本文は先頭の30文字（超えたら「…」） */
  public record Target(long id, String bodyExcerpt) {}

  /**
   * @param iconUrlOf アイコンの S3 のキーから、署名付き URL を作る（なければ null）
   */
  static NotificationResponse from(NotificationRow row, Function<String, String> iconUrlOf) {
    return new NotificationResponse(
        row.id(),
        row.type(),
        JapanTime.of(row.createdAt()),
        row.readAt() != null,
        row.actorUsername() == null
            ? null
            : new Actor(
                row.actorUsername(),
                row.actorDisplayName(),
                iconUrlOf.apply(row.actorIconKey()),
                row.actorPrivate()),
        row.workoutId() == null
            ? null
            : new Target(row.workoutId(), Excerpt.of(row.workoutBody(), EXCERPT_LENGTH)),
        row.commentId() == null
            ? null
            : new Target(row.commentId(), Excerpt.of(row.commentBody(), EXCERPT_LENGTH)),
        null);
  }
}
