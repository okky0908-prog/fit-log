package com.okimoto.fitlog.auth.controller;

import com.okimoto.fitlog.image.service.ImageUrls;
import com.okimoto.fitlog.jooq.tables.records.UsersRecord;

/**
 * ログイン中のユーザー（docs/api.md の Me。A-01〜A-03 の {@code user}）。
 *
 * @param username ユーザー名
 * @param displayName 表示名
 * @param iconUrl アイコンの署名付き URL。なければ null
 * @param bio 自己紹介。なければ null
 * @param email メールアドレス
 * @param emailVerified メールアドレスの確認が済んでいるか
 * @param isPrivate 鍵アカウントか
 * @param weightUnit 重さの単位（kg・lb）
 * @param distanceUnit 距離の単位（km・mi）
 */
public record MeResponse(
    String username,
    String displayName,
    String iconUrl,
    String bio,
    String email,
    boolean emailVerified,
    boolean isPrivate,
    String weightUnit,
    String distanceUnit) {

  static MeResponse from(UsersRecord user, ImageUrls imageUrls) {
    return new MeResponse(
        user.getUsername(),
        user.getDisplayName(),
        imageUrls.iconUrlOf(user.getIconKey()),
        user.getBio(),
        user.getEmail(),
        user.getEmailVerifiedAt() != null,
        user.getIsPrivate(),
        user.getWeightUnit(),
        user.getDistanceUnit());
  }
}
