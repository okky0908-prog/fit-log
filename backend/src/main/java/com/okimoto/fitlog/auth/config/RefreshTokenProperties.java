package com.okimoto.fitlog.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * リフレッシュトークンの設定（application.yml の app.refresh-token）。
 *
 * @param expiration 有効期限（14日）。この間ログイン状態が続く
 * @param cookieSecure Cookie に Secure 属性を付けるか。HTTPS の本番では true、HTTP の手元の開発では false
 * @param reuseGrace 交換したトークンが、この時間内にもう一度使われたら「同時のリクエスト」として許す（10秒）
 * @param cleanupCron 期限切れの行を消す時刻（cron。日本時間）
 */
@ConfigurationProperties("app.refresh-token")
public record RefreshTokenProperties(
    Duration expiration, boolean cookieSecure, Duration reuseGrace, String cleanupCron) {}
