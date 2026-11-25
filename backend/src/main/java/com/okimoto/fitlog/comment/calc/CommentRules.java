package com.okimoto.fitlog.comment.calc;

import com.okimoto.fitlog.common.controller.ApiError.FieldError;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** コメント・返信の本文の整え方とチェック（docs/api.md A-61）と、通知を作るかの決まり（docs/test-plan.md 5.9） */
public final class CommentRules {

  public static final String BODY_MESSAGE = "コメントは1〜140文字で入力してください";

  /** コメントの通知の種類 */
  public static final String COMMENT = "comment";

  /** 返信の通知の種類 */
  public static final String REPLY = "reply";

  private CommentRules() {}

  /**
   * 作る通知の1件。
   *
   * @param type 種類（comment・reply）
   * @param recipientId 受け取る人
   */
  public record Notice(String type, long recipientId) {}

  /** 本文の文字数の上限 */
  static final int BODY_MAX = 140;

  /** 保存する本文。改行を \n にそろえ（ブラウザはフォームで \r\n を送る）、前後の空白・改行を除く。null は null のまま */
  public static String body(String value) {
    return value == null ? null : value.replace("\r\n", "\n").strip();
  }

  /** 本文の誤り。整えたあとの文字数を、コードポイントで数える（絵文字も1文字。画面の {@code [...text].length} と同じ） */
  public static Optional<FieldError> problem(String body) {
    String text = body(body);
    int length = text == null ? 0 : text.codePointCount(0, text.length());
    return length < 1 || length > BODY_MAX
        ? Optional.of(new FieldError("body", BODY_MESSAGE))
        : Optional.empty();
  }

  /**
   * 作る通知（F-110）。コメントは投稿者に comment。返信は返信先の人に reply、投稿者に comment（同じ人なら reply の1件だけ）。 自分への通知は作らない。
   *
   * @param replyToUserId 返信先の人。返信でなければ null
   * @return 作る通知（返信先 → 投稿者の順）
   */
  public static List<Notice> notices(long actorId, long workoutAuthorId, Long replyToUserId) {
    List<Notice> notices = new ArrayList<>();
    boolean replyToOther = replyToUserId != null && replyToUserId != actorId;
    if (replyToOther) {
      notices.add(new Notice(REPLY, replyToUserId));
    }
    // 返信先が投稿者なら reply の1件だけにする（2件にしない）
    boolean authorNotified = replyToOther && replyToUserId == workoutAuthorId;
    if (!authorNotified && workoutAuthorId != actorId) {
      notices.add(new Notice(COMMENT, workoutAuthorId));
    }
    return List.copyOf(notices);
  }
}
