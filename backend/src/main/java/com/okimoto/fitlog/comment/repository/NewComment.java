package com.okimoto.fitlog.comment.repository;

import java.time.OffsetDateTime;

/**
 * 新しく保存するコメント・返信。
 *
 * @param workoutId 投稿
 * @param userId 書いた人
 * @param parentId 返信元のコメント。返信でなければ null
 * @param replyToUserId 返信先の人。返信でなければ null
 * @param body 本文（整えたあと）
 * @param createdAt 書いた日時
 */
public record NewComment(
    long workoutId,
    long userId,
    Long parentId,
    Long replyToUserId,
    String body,
    OffsetDateTime createdAt) {}
