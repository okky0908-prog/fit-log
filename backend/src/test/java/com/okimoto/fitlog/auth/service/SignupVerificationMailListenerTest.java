package com.okimoto.fitlog.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.okimoto.fitlog.common.controller.ApiException;
import com.okimoto.fitlog.common.controller.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * 新規登録のあとの確認メールの送信。保存の確定後に別のスレッドで動くこと・Mailpit に届くことは、結合テスト（EmailVerificationIntegrationTest）で確かめる
 */
class SignupVerificationMailListenerTest {

  private final EmailVerificationService emailVerificationService =
      mock(EmailVerificationService.class);
  private final SignupVerificationMailListener listener =
      new SignupVerificationMailListener(emailVerificationService);

  @Test
  void 登録したユーザーのアドレスに確認メールを送る() {
    listener.onUserRegistered(new UserRegisteredEvent(7L, "yamada@example.com"));

    verify(emailVerificationService).send(7L, "yamada@example.com");
  }

  /** 送れなくても登録は成功のまま（画面の「確認メールを再送」で送り直せる）。失敗はログに残す */
  @Test
  void 送れなくても例外を外に出さない() {
    willThrow(new ApiException(ErrorCode.SERVICE_UNAVAILABLE))
        .given(emailVerificationService)
        .send(7L, "yamada@example.com");

    assertThatCode(
            () -> listener.onUserRegistered(new UserRegisteredEvent(7L, "yamada@example.com")))
        .doesNotThrowAnyException();
  }
}
