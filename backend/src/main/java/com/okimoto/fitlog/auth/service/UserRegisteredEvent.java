package com.okimoto.fitlog.auth.service;

/**
 * 新規登録した（保存の確定後に、確認メールを送る）。
 *
 * @param userId 登録したユーザー
 * @param email 確認するメールアドレス（小文字にそろえたもの）
 */
public record UserRegisteredEvent(long userId, String email) {}
