package com.okimoto.fitlog.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * メールアドレスの確認の設定（application.yml の app.email-verification）。
 *
 * @param expiration 確認用のリンクの有効期限（24時間。発行からちょうど24時間で使えなくなる）
 * @param linkUrl 確認の画面（S-05）の URL。ここに {@code ?token=…} を付けてメールに書く
 */
@ConfigurationProperties(prefix = "app.email-verification")
public record EmailVerificationProperties(Duration expiration, String linkUrl) {}
