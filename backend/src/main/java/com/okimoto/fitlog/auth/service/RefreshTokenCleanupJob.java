package com.okimoto.fitlog.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 期限切れのリフレッシュトークンを、毎日消す */
@Component
public class RefreshTokenCleanupJob {

  private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

  private final RefreshTokenService refreshTokenService;

  public RefreshTokenCleanupJob(RefreshTokenService refreshTokenService) {
    this.refreshTokenService = refreshTokenService;
  }

  /** 毎日 4:00（日本時間。利用者の少ない時間）。時刻は app.refresh-token.cleanup-cron で変えられる */
  @Scheduled(cron = "${app.refresh-token.cleanup-cron}", zone = "Asia/Tokyo")
  public void run() {
    log.info("期限切れのリフレッシュトークンを {} 件消しました", refreshTokenService.deleteExpired());
  }
}
