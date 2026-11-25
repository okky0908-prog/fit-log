package com.okimoto.fitlog.comment.controller;

import com.okimoto.fitlog.comment.repository.CommentRepository;
import com.okimoto.fitlog.comment.repository.CommentRow;
import com.okimoto.fitlog.comment.repository.CommentUser;
import com.okimoto.fitlog.comment.repository.ReplyRow;
import com.okimoto.fitlog.common.calc.Cursor;
import com.okimoto.fitlog.common.calc.JapanTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;

/** コメント・返信の応答の形（docs/api.md「Comment」）。返信には replies・replyCount・repliesNextCursor を付けない（返信は1段だけ） */
public final class CommentResponses {

  private CommentResponses() {}

  /** 応答の1件（コメントか返信） */
  public sealed interface Item permits CommentResponse, ReplyResponse {}

  /** 書いた人・返信先の人（docs/api.md「UserSummary」） */
  public record User(String username, String displayName, String iconUrl, boolean isPrivate) {

    static User from(CommentUser user, Function<String, String> iconUrlOf) {
      return user == null
          ? null
          : new User(
              user.username(),
              user.displayName(),
              iconUrlOf.apply(user.iconKey()),
              user.isPrivate());
    }
  }

  /**
   * 返信でないコメント。
   *
   * @param id コメント
   * @param author 書いた人
   * @param body 本文
   * @param createdAt 書いた日時（日本時間）
   * @param deletable 自分が書いたか、自分の投稿へのコメントか（「削除」を出すか）
   * @param replies 返信（古い順に3件まで）
   * @param replyCount 返信の総数
   * @param repliesNextCursor 4件目からを A-65 で取るカーソル。3件以下なら null
   */
  public record CommentResponse(
      long id,
      User author,
      String body,
      OffsetDateTime createdAt,
      boolean deletable,
      List<ReplyResponse> replies,
      int replyCount,
      String repliesNextCursor)
      implements Item {

    static CommentResponse from(CommentRow row, Function<String, String> iconUrlOf) {
      List<ReplyRow> replies = row.replies();
      String next =
          row.replyCount() > CommentRepository.REPLIES_IN_COMMENT
              ? Cursor.encode(replies.getLast().createdAt(), replies.getLast().id())
              : null;
      return new CommentResponse(
          row.id(),
          User.from(row.author(), iconUrlOf),
          row.body(),
          JapanTime.of(row.createdAt()),
          row.deletable(),
          replies.stream().map(reply -> ReplyResponse.from(reply, iconUrlOf)).toList(),
          row.replyCount(),
          next);
    }

    /** 書いたばかりのコメント（返信はまだない） */
    static CommentResponse posted(ReplyRow row, Function<String, String> iconUrlOf) {
      return new CommentResponse(
          row.id(),
          User.from(row.author(), iconUrlOf),
          row.body(),
          JapanTime.of(row.createdAt()),
          row.deletable(),
          List.of(),
          0,
          null);
    }
  }

  /**
   * 返信。
   *
   * @param id 返信
   * @param author 書いた人
   * @param replyToUser 返信先の人。退会していれば null（画面は @xxx を出さない）
   * @param body 本文
   * @param createdAt 書いた日時（日本時間）
   * @param deletable 自分が書いたか、自分の投稿への返信か
   */
  public record ReplyResponse(
      long id,
      User author,
      User replyToUser,
      String body,
      OffsetDateTime createdAt,
      boolean deletable)
      implements Item {

    static ReplyResponse from(ReplyRow row, Function<String, String> iconUrlOf) {
      return new ReplyResponse(
          row.id(),
          User.from(row.author(), iconUrlOf),
          User.from(row.replyToUser(), iconUrlOf),
          row.body(),
          JapanTime.of(row.createdAt()),
          row.deletable());
    }
  }
}
