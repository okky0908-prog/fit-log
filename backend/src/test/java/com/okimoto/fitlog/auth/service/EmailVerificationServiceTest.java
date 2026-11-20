package com.okimoto.fitlog.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.okimoto.fitlog.auth.config.EmailVerificationProperties;
import com.okimoto.fitlog.auth.repository.EmailVerificationTokenRepository;
import com.okimoto.fitlog.common.controller.ApiException;
import com.okimoto.fitlog.jooq.tables.records.EmailVerificationTokensRecord;
import com.okimoto.fitlog.jooq.tables.records.UsersRecord;
import com.okimoto.fitlog.mail.repository.MailType;
import com.okimoto.fitlog.mail.service.MailGateway;
import com.okimoto.fitlog.mail.service.MailSendLimiter;
import com.okimoto.fitlog.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.transaction.support.TransactionOperations;

/**
 * メールアドレスの確認（A-01 の確認メールの送信・A-06・A-07）。状態遷移は docs/test-plan.md 5.7「確認用のトークン」。
 *
 * <p>メールアドレスの変更（A-10）にかかわる行（変更の確認待ち・email_changed）は、段階 5 で A-10 と一緒に作る
 */
class EmailVerificationServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-20T01:00:00Z");
  private static final OffsetDateTime NOW_ODT = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
  private static final long USER_ID = 7L;
  private static final String EMAIL = "yamada@example.com";
  private static final String LINK_URL = "http://localhost:5173/verify-email";
  private static final Pattern LINK = Pattern.compile(Pattern.quote(LINK_URL) + "\\?token=(\\S+)");

  private final EmailVerificationTokenRepository tokenRepository =
      mock(EmailVerificationTokenRepository.class);
  private final UserRepository userRepository = mock(UserRepository.class);
  private final MailSendLimiter mailSendLimiter = mock(MailSendLimiter.class);
  private final MailGateway mailGateway = mock(MailGateway.class);
  private final EmailVerificationService service =
      new EmailVerificationService(
          tokenRepository,
          userRepository,
          mailSendLimiter,
          mailGateway,
          new EmailVerificationProperties(Duration.ofHours(24), LINK_URL),
          TransactionOperations.withoutTransaction(),
          Clock.fixed(NOW, ZoneOffset.UTC));

  // ===== 確認メールを送る（A-01 の送信・A-07） =====

  @Test
  void 確認メールを送ると_ハッシュだけを24時間の期限で保存し_リンクを送る() {
    given(mailSendLimiter.tryRecord(EMAIL, MailType.VERIFY_EMAIL)).willReturn(true);

    service.send(USER_ID, EMAIL);

    ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
    verify(mailGateway).send(eq(EMAIL), eq("【FIT LOG】メールアドレスの確認"), text.capture());
    String token = tokenIn(text.getValue());
    verify(tokenRepository)
        .upsert(USER_ID, EMAIL, OpaqueTokens.hash(token), NOW_ODT.plusHours(24), NOW_ODT);
    assertThat(text.getValue()).contains("24時間");
  }

  @Test
  void 送信の上限を超えたら_429で_トークンを上書きせず送らない() {
    given(mailSendLimiter.tryRecord(EMAIL, MailType.VERIFY_EMAIL)).willReturn(false);

    assertApiError(() -> service.send(USER_ID, EMAIL), "TOO_MANY_EMAILS");

    // 前のリンクは使えるまま
    verify(tokenRepository, never()).upsert(anyLong(), any(), any(), any(), any());
    verifyNoInteractions(mailGateway);
  }

  @Test
  void メールを送れなかったら_503() {
    given(mailSendLimiter.tryRecord(EMAIL, MailType.VERIFY_EMAIL)).willReturn(true);
    willThrow(new MailSendException("つながらない"))
        .given(mailGateway)
        .send(anyString(), anyString(), anyString());

    assertApiError(() -> service.send(USER_ID, EMAIL), "SERVICE_UNAVAILABLE");
  }

  // ===== A-07 確認メールの再送 =====

  @Test
  void 未確認なら_今のアドレスに確認メールを送り直す() {
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(null)));
    given(mailSendLimiter.tryRecord(EMAIL, MailType.VERIFY_EMAIL)).willReturn(true);

    service.resend(USER_ID);

    verify(mailGateway).send(eq(EMAIL), anyString(), anyString());
  }

  @Test
  void 確認済みなら_409で送らない() {
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(NOW_ODT.minusDays(1))));

    assertApiError(() -> service.resend(USER_ID), "ALREADY_VERIFIED");

    verifyNoInteractions(mailSendLimiter, mailGateway);
  }

  @Test
  void ユーザーがいない_退会した_なら401() {
    given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

    assertApiError(() -> service.resend(USER_ID), "UNAUTHENTICATED");
  }

  // ===== A-06 メールアドレスの確認 =====

  @Test
  void 期限の1秒前のトークンで確認でき_確認済みにして_アドレスを返す() {
    given(tokenRepository.takeByTokenHash(OpaqueTokens.hash("t")))
        .willReturn(Optional.of(token(NOW_ODT.plusSeconds(1))));

    assertThat(service.verify("t")).isEqualTo(EMAIL);

    verify(userRepository).markEmailVerified(USER_ID, NOW_ODT);
  }

  @Test
  void 期限ちょうどのトークンは400で_確認済みにしない() {
    given(tokenRepository.takeByTokenHash(any())).willReturn(Optional.of(token(NOW_ODT)));

    assertApiError(() -> service.verify("t"), "INVALID_TOKEN");

    verify(userRepository, never()).markEmailVerified(anyLong(), any());
  }

  /** 使用済み（取り出すと行が消えるので、2回目は見つからない）・上書きされた前のリンク・でたらめな値 */
  @Test
  void 見つからないトークンは400() {
    given(tokenRepository.takeByTokenHash(any())).willReturn(Optional.empty());

    assertApiError(() -> service.verify("t"), "INVALID_TOKEN");
  }

  @Test
  void トークンがない_空なら400で_DBを見ない() {
    assertApiError(() -> service.verify(null), "INVALID_TOKEN");
    assertApiError(() -> service.verify(" "), "INVALID_TOKEN");

    verifyNoInteractions(tokenRepository);
  }

  @Test
  void 期限切れのトークンを今の時刻で消す() {
    given(tokenRepository.deleteExpired(NOW_ODT)).willReturn(2);

    assertThat(service.deleteExpired()).isEqualTo(2);
  }

  private static String tokenIn(String text) {
    Matcher matcher = LINK.matcher(text);
    assertThat(matcher.find()).as("本文にリンクがある：%s", text).isTrue();
    return matcher.group(1);
  }

  private static UsersRecord user(OffsetDateTime emailVerifiedAt) {
    UsersRecord user = new UsersRecord();
    user.setId(USER_ID);
    user.setEmail(EMAIL);
    user.setEmailVerifiedAt(emailVerifiedAt);
    return user;
  }

  private static EmailVerificationTokensRecord token(OffsetDateTime expiresAt) {
    EmailVerificationTokensRecord token = new EmailVerificationTokensRecord();
    token.setUserId(USER_ID);
    token.setEmail(EMAIL);
    token.setExpiresAt(expiresAt);
    return token;
  }

  private static void assertApiError(ThrowingCallable call, String code) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.toApiError().code()).isEqualTo(code));
  }
}
