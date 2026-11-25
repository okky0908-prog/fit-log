package com.okimoto.fitlog.comment.repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 返信でないコメント（A-60 の1件）。
 *
 * @param id コメント
 * @param author 書いた人
 * @param body 本文
 * @param createdAt 書いた日時
 * @param deletable 見ている人が消せるか（書いた本人か、投稿者）
 * @param replies 返信（古い順に3件まで）
 * @param replyCount 返信の総数
 */
public record CommentRow(
    long id,
    CommentUser author,
    String body,
    OffsetDateTime createdAt,
    boolean deletable,
    List<ReplyRow> replies,
    int replyCount) {}
