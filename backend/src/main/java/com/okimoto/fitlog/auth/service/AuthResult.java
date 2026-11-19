package com.okimoto.fitlog.auth.service;

import com.okimoto.fitlog.jooq.tables.records.UsersRecord;

/**
 * 新規登録・ログインの結果。
 *
 * @param accessToken アクセストークン（JWT）
 * @param user ログインしたユーザー
 */
public record AuthResult(String accessToken, UsersRecord user) {}
