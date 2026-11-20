package com.okimoto.fitlog.auth.service;

import com.okimoto.fitlog.jooq.tables.records.UsersRecord;

/**
 * 新規登録・ログイン・再発行の結果。
 *
 * @param accessToken アクセストークン（JWT）
 * @param refreshToken リフレッシュトークン（Cookie で渡す値）
 * @param user ログインしたユーザー
 */
public record AuthResult(String accessToken, String refreshToken, UsersRecord user) {}
