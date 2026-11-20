package com.okimoto.fitlog.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ログインの試行回数の制限（application.yml の app.login-attempt。前回と同じ）。
 *
 * @param maxFailures この回数だけ失敗したらロックする（5回）
 * @param window 失敗を数える期間（15分）。最初の失敗からこの時間がたったら、数え直す
 * @param lockDuration ロックする時間（15分）
 */
@ConfigurationProperties("app.login-attempt")
public record LoginAttemptProperties(int maxFailures, Duration window, Duration lockDuration) {}
