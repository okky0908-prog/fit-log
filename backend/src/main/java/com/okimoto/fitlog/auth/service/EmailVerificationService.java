package com.okimoto.fitlog.auth.service;

import com.okimoto.fitlog.auth.config.EmailVerificationProperties;
import com.okimoto.fitlog.auth.repository.EmailVerificationTokenRepository;
import com.okimoto.fitlog.common.controller.ApiException;
import com.okimoto.fitlog.common.controller.ErrorCode;
import com.okimoto.fitlog.jooq.tables.records.EmailVerificationTokensRecord;
import com.okimoto.fitlog.jooq.tables.records.UsersRecord;
import com.okimoto.fitlog.mail.repository.MailType;
import com.okimoto.fitlog.mail.service.MailGateway;
import com.okimoto.fitlog.mail.service.MailSendLimiter;
import com.okimoto.fitlog.user.repository.UserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * メールアドレスの確認（A-01 の確認メールの送信・A-06・A-07。docs/api.md）。
 *
 * <ul>
 *   <li>確認用のリンクのトークンは1人に1つ。新しいリンクを送ると、前のリンクは使えなくなる
 *   <li>リンクは24時間（発行からちょうど24時間で使えなくなる）・1回だけ使える
 *   <li>同じアドレスに1時間で5通まで（{@link MailSendLimiter}）
 * </ul>
 *
 * <p>メールアドレスの変更（A-10）の確認（トークンのアドレスが今のアドレスと違う行）は、段階 5 で A-10 と一緒に作る。それまでは、トークンのアドレスはいつも今のアドレス
 */
@Service
public class EmailVerificationService {

  static final String SUBJECT = "【FIT LOG】メールアドレスの確認";

  private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

  private final EmailVerificationTokenRepository tokenRepository;
  private final UserRepository userRepository;
  private final MailSendLimiter mailSendLimiter;
  private final MailGateway mailGateway;
  private final EmailVerificationProperties properties;
  private final TransactionOperations transactionOperations;
  private final Clock clock;

  public EmailVerificationService(
      EmailVerificationTokenRepository tokenRepository,
      UserRepository userRepository,
      MailSendLimiter mailSendLimiter,
      MailGateway mailGateway,
      EmailVerificationProperties properties,
      TransactionOperations transactionOperations,
      Clock clock) {
    this.tokenRepository = tokenRepository;
    this.userRepository = userRepository;
    this.mailSendLimiter = mailSendLimiter;
    this.mailGateway = mailGateway;
    this.properties = properties;
    this.transactionOperations = transactionOperations;
    this.clock = clock;
  }

  /**
   * 確認用のリンクを発行して、メールで送る（新規登録のあと・A-07）。
   *
   * <p>上限の確認・送信の記録・トークンの保存は1つのトランザクションで確定し、メールはその後に送る（SMTP の応答を待つ間、DB の接続とアドレスのロックを持たないため）
   *
   * @throws ApiException 上限を超えた（429 TOO_MANY_EMAILS。前のリンクは使えるまま）・メールを送れなかった（503
   *     SERVICE_UNAVAILABLE）
   */
  public void send(long userId, String email) {
    String token = OpaqueTokens.generate();
    OffsetDateTime now = OffsetDateTime.now(clock);
    transactionOperations.executeWithoutResult(status -> {
      if (!mailSendLimiter.tryRecord(email, MailType.VERIFY_EMAIL)) {
        throw new ApiException(ErrorCode.TOO_MANY_EMAILS);
      }
      tokenRepository.upsert(
          userId, email, OpaqueTokens.hash(token), now.plus(properties.expiration()), now);
    });
    try {
      mailGateway.send(email, SUBJECT, body(token));
    } catch (MailException e) {
      log.warn("確認メールを送れませんでした（ユーザー ID {}）", userId, e);
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
    }
  }

  /** A-07 確認メールの再送。確認済みなら 409（ALREADY_VERIFIED） */
  public void resend(long userId) {
    UsersRecord user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    if (user.getEmailVerifiedAt() != null) {
      throw new ApiException(ErrorCode.ALREADY_VERIFIED);
    }
    send(user.getId(), user.getEmail());
  }

  /**
   * A-06 メールアドレスの確認。トークンは取り出すと消えるので、1回だけ使える
   *
   * @return 確認したメールアドレス
   * @throws ApiException 期限切れ（期限ちょうどを含む）・使用済み・不正（400 INVALID_TOKEN）
   */
  public String verify(String token) {
    if (token == null || token.isBlank()) {
      throw new ApiException(ErrorCode.INVALID_TOKEN);
    }
    OffsetDateTime now = OffsetDateTime.now(clock);
    return transactionOperations.execute(status -> {
      EmailVerificationTokensRecord found =
          tokenRepository
              .takeByTokenHash(OpaqueTokens.hash(token))
              .filter(t -> t.getExpiresAt().isAfter(now))
              .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN));
      userRepository.markEmailVerified(found.getUserId(), now);
      return found.getEmail();
    });
  }

  /**
   * 期限切れ（期限ちょうどを含む）の確認用のトークンを消す（毎日の掃除）
   *
   * @return 消した件数
   */
  public int deleteExpired() {
    return tokenRepository.deleteExpired(OffsetDateTime.now(clock));
  }

  private String body(String token) {
    return """
    FIT LOG にご登録いただき、ありがとうございます。

    次のリンクを開いて、メールアドレスの確認を済ませてください。
    %s?token=%s

    このリンクの有効期限は%d時間です。期限が切れたときは、FIT LOG にログインして「確認メールを再送」から送り直してください。
    お心当たりがない場合は、このメールを破棄してください。
    """
        .formatted(properties.linkUrl(), token, properties.expiration().toHours());
  }
}
