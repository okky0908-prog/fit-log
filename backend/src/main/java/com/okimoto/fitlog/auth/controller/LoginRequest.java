package com.okimoto.fitlog.auth.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * ログインのリクエスト（A-02）。形の決まり（文字数など）は確かめない（登録のときの決まりを知らせないため。合わなければ INVALID_CREDENTIALS）
 *
 * @param email メールアドレス
 * @param password パスワード
 */
public record LoginRequest(
    @NotBlank(message = "メールアドレスを入力してください") String email,
    @NotEmpty(message = "パスワードを入力してください") String password) {}
