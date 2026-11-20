package com.okimoto.fitlog.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 新規登録のあとに確認メールを送る（docs/api.md A-01）。
 *
 * <ul>
 *   <li>登録の保存が確定したあとに動く（{@code @TransactionalEventListener} の既定は AFTER_COMMIT）。登録が取り消されたら送らない
 *   <li>別のスレッドで動く（{@code @Async}）。SMTP の応答を、登録の応答に待たせない
 *   <li>送れなくても登録は成功のまま。失敗はログに残す（画面の「確認メールを再送」で送り直せる。docs/tech-stack.md 3.6）
 * </ul>
 */
@Component
public class SignupVerificationMailListener {

  private static final Logger log = LoggerFactory.getLogger(SignupVerificationMailListener.class);

  private final EmailVerificationService emailVerificationService;

  public SignupVerificationMailListener(EmailVerificationService emailVerificationService) {
    this.emailVerificationService = emailVerificationService;
  }

  @Async
  @TransactionalEventListener
  public void onUserRegistered(UserRegisteredEvent event) {
    try {
      emailVerificationService.send(event.userId(), event.email());
    } catch (RuntimeException e) {
      log.warn("新規登録の確認メールを送れませんでした（ユーザー ID {}）", event.userId(), e);
    }
  }
}
