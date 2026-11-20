package com.okimoto.fitlog.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * パスワードのハッシュ・照合（BCrypt）を同時に動かす数の制限（application.yml の app.password-hashing。前回と同じ）。
 *
 * @param maxConcurrent 同時に動かす数の上限
 * @param maxWait 空きを待つ時間の上限。待っても空かなければ、混み合っているとして 503 を返す
 */
@ConfigurationProperties("app.password-hashing")
public record PasswordHashingProperties(int maxConcurrent, Duration maxWait) {}
