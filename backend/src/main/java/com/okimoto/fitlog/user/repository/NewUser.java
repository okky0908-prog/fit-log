package com.okimoto.fitlog.user.repository;

import java.time.OffsetDateTime;

/**
 * 新しく保存するユーザー。
 *
 * @param username ユーザー名
 * @param displayName 表示名（前後の空白を除いたもの）
 * @param email メールアドレス（小文字にそろえたもの）
 * @param passwordHash パスワードのハッシュ（BCrypt）
 * @param createdAt 登録した日時
 */
public record NewUser(
    String username,
    String displayName,
    String email,
    String passwordHash,
    OffsetDateTime createdAt) {}
