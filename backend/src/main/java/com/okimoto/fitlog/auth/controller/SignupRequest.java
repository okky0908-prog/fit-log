package com.okimoto.fitlog.auth.controller;

import com.okimoto.fitlog.common.controller.MaxBytes;
import com.okimoto.fitlog.common.controller.TextLength;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新規登録のリクエスト（A-01）。入力の決まりは docs/features.md「ユーザー情報の項目」、境界値は docs/test-plan.md 5.1。
 *
 * @param username ユーザー名（半角英数字と _ で4〜15文字）
 * @param displayName 表示名（1〜50文字）
 * @param email メールアドレス（前後の空白を除き、小文字にそろえて保存する）
 * @param password パスワード（8〜72文字・72バイトまで）
 */
public record SignupRequest(
    @NotNull(message = USERNAME_MESSAGE)
        @Pattern(regexp = "^[A-Za-z0-9_]{4,15}$", message = USERNAME_MESSAGE)
        String username,
    @TextLength(min = 1, max = 50, message = "表示名は1〜50文字で入力してください") String displayName,
    @NotBlank(message = EMAIL_MESSAGE)
        @Email(regexp = ".+@.+\\..+", message = EMAIL_MESSAGE)
        @Size(max = 254, message = EMAIL_MESSAGE)
        String email,
    // 下限と上限で文言を分けるため、@Size を2つ重ねる
    @NotNull(message = PASSWORD_MIN_MESSAGE)
        @Size(min = 8, message = PASSWORD_MIN_MESSAGE)
        @Size(max = 72, message = "パスワードは72文字以内で入力してください")
        @MaxBytes(value = 72, message = "パスワードが長すぎます（全角文字は1文字を3バイトとして、72バイトまで）")
        String password) {

  static final String USERNAME_MESSAGE = "ユーザー名は半角英数字と_で4〜15文字で入力してください";
  static final String EMAIL_MESSAGE = "メールアドレスの形式が正しくありません";
  static final String PASSWORD_MIN_MESSAGE = "パスワードは8文字以上で入力してください";
}
