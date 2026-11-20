package com.okimoto.fitlog.mail.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * メールの送信の設定（application.yml の app.mail）。
 *
 * @param from 送信元のアドレス
 * @param maxPerWindow 同じアドレスに、window の間に送れる数（5通）
 * @param window 送信の上限を数える期間（1時間）
 * @param logRetention 送信の記録を残す期間（1日。毎日の掃除でこれより古い記録を消す）
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailSendProperties(
    String from, int maxPerWindow, Duration window, Duration logRetention) {}
