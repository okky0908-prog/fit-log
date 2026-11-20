package com.okimoto.fitlog.auth.controller;

import com.okimoto.fitlog.auth.config.AuthenticatedUser;
import com.okimoto.fitlog.auth.service.EmailVerificationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** メールアドレスの確認（A-06）・確認メールの再送（A-07）。docs/api.md */
@RestController
public class EmailVerificationController {

  private final EmailVerificationService emailVerificationService;

  public EmailVerificationController(EmailVerificationService emailVerificationService) {
    this.emailVerificationService = emailVerificationService;
  }

  /**
   * A-06 メールアドレスの確認。ログインしていなくても呼べる（別の端末でメールを開くことがあるため）。
   *
   * <p>トークンを URL のクエリではなく本文で受け取るのは、サーバーのアクセスログに残さないため
   */
  @PostMapping("/api/auth/email-verification")
  public VerifyEmailResponse verify(@RequestBody VerifyEmailRequest request) {
    return VerifyEmailResponse.verified(emailVerificationService.verify(request.token()));
  }

  /** A-07 確認メールの再送 */
  @PostMapping("/api/account/email-verification")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void resend(@AuthenticationPrincipal AuthenticatedUser user) {
    emailVerificationService.resend(user.id());
  }
}
