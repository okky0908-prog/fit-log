package com.okimoto.fitlog.comment.repository;

import java.time.OffsetDateTime;

/**
 * 返信（A-60 の replies の1件・A-65 の1件）。
 *
 * @param id 返信
 * @param author 書いた人
 * @param replyToUser 返信先の人。退会していれば null
 * @param body 本文
 * @param createdAt 書いた日時
 * @param deletable 見ている人が消せるか（書いた本人か、投稿者）
 */
public record ReplyRow(
    long id,
    CommentUser author,
    CommentUser replyToUser,
    String body,
    OffsetDateTime createdAt,
    boolean deletable) {}
