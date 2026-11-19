package com.okimoto.fitlog.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT の設定（application.yml の app.jwt）。
 *
 * @param secret 署名の鍵。32バイト以上。環境変数（.env）の JWT_SECRET で渡す
 * @param expiration アクセストークンの有効期限（15分。docs/api.md「共通のルール」）
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, Duration expiration) {}
