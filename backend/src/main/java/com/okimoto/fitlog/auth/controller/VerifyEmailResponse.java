package com.okimoto.fitlog.auth.controller;

/**
 * A-06 のレスポンス。
 *
 * @param result {@code verified}（メールアドレスの確認）。メールアドレスの変更の確認（{@code email_changed}）は段階 5 で足す
 * @param email 確認したメールアドレス
 */
public record VerifyEmailResponse(String result, String email) {

  static VerifyEmailResponse verified(String email) {
    return new VerifyEmailResponse("verified", email);
  }
}
