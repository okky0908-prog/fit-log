package com.okimoto.fitlog.auth.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.okimoto.fitlog.mail.service.MailSendLimiter;
import org.junit.jupiter.api.Test;

/** 毎日の掃除（docs/database.md「refresh_tokens」「email_verification_tokens」「mail_send_logs」） */
class DailyCleanupJobTest {

  private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
  private final EmailVerificationService emailVerificationService =
      mock(EmailVerificationService.class);
  private final MailSendLimiter mailSendLimiter = mock(MailSendLimiter.class);
  private final DailyCleanupJob job =
      new DailyCleanupJob(refreshTokenService, emailVerificationService, mailSendLimiter);

  @Test
  void 期限切れのトークンと古い送信の記録を消す() {
    job.run();

    verify(refreshTokenService).deleteExpired();
    verify(emailVerificationService).deleteExpired();
    verify(mailSendLimiter).deleteOldLogs();
  }
}
