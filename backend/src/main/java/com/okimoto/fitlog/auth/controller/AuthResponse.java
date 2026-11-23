package com.okimoto.fitlog.auth.controller;

import com.okimoto.fitlog.auth.service.AuthResult;
import com.okimoto.fitlog.image.service.ImageUrls;

/**
 * 新規登録・ログインのレスポンス（A-01・A-02）。
 *
 * @param accessToken アクセストークン（JWT、15分）。{@code Authorization: Bearer} で送る
 * @param user ログインしたユーザー
 */
public record AuthResponse(String accessToken, MeResponse user) {

  static AuthResponse from(AuthResult result, ImageUrls imageUrls) {
    return new AuthResponse(result.accessToken(), MeResponse.from(result.user(), imageUrls));
  }
}
