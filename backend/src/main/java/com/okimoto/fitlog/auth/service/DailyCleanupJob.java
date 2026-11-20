package com.okimoto.fitlog.auth.service;

import com.okimoto.fitlog.mail.service.MailSendLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 毎日の掃除。期限切れのリフレッシュトークン・確認用のトークンと、1日より古いメールの送信の記録を消す（メールアドレスを長く残さないため） */
@Component
public class DailyCleanupJob {

  private static final Logger log = LoggerFactory.getLogger(DailyCleanupJob.class);

  private final RefreshTokenService refreshTokenService;
  private final EmailVerificationService emailVerificationService;
  private final MailSendLimiter mailSendLimiter;

  public DailyCleanupJob(
      RefreshTokenService refreshTokenService,
      EmailVerificationService emailVerificationService,
      MailSendLimiter mailSendLimiter) {
    this.refreshTokenService = refreshTokenService;
    this.emailVerificationService = emailVerificationService;
    this.mailSendLimiter = mailSendLimiter;
  }

  /** 毎日 4:00（日本時間。利用者の少ない時間）。時刻は app.cleanup-cron で変えられる */
  @Scheduled(cron = "${app.cleanup-cron}", zone = "Asia/Tokyo")
  public void run() {
    log.info(
        "毎日の掃除：リフレッシュトークン {} 件・確認用のトークン {} 件・メールの送信の記録 {} 件を消しました",
        refreshTokenService.deleteExpired(),
        emailVerificationService.deleteExpired(),
        mailSendLimiter.deleteOldLogs());
  }
}
