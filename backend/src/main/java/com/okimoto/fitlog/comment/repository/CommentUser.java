package com.okimoto.fitlog.comment.repository;

/**
 * コメントを書いた人・返信先の人（docs/api.md「UserSummary」の元）。
 *
 * @param username ユーザー名
 * @param displayName 表示名
 * @param isPrivate 鍵アカウントか
 * @param iconKey アイコンの S3 のキー。なければ null
 */
public record CommentUser(String username, String displayName, boolean isPrivate, String iconKey) {}
