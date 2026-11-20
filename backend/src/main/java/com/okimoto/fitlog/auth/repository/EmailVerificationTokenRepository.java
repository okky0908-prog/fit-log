package com.okimoto.fitlog.auth.repository;

import static com.okimoto.fitlog.jooq.Tables.EMAIL_VERIFICATION_TOKENS;

import com.okimoto.fitlog.jooq.tables.records.EmailVerificationTokensRecord;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/** メールアドレスの確認用のトークン（email_verification_tokens）の読み書き。日時はすべて引数で受け取る */
@Repository
public class EmailVerificationTokenRepository {

  private final DSLContext dsl;

  public EmailVerificationTokenRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** トークンを保存する。そのユーザーの行がすでにあれば上書きするので、前のトークンは使えなくなる（1人に1行） */
  public void upsert(
      long userId, String email, String tokenHash, OffsetDateTime expiresAt, OffsetDateTime now) {
    dsl.insertInto(EMAIL_VERIFICATION_TOKENS)
        .set(EMAIL_VERIFICATION_TOKENS.USER_ID, userId)
        .set(EMAIL_VERIFICATION_TOKENS.EMAIL, email)
        .set(EMAIL_VERIFICATION_TOKENS.TOKEN_HASH, tokenHash)
        .set(EMAIL_VERIFICATION_TOKENS.EXPIRES_AT, expiresAt)
        .set(EMAIL_VERIFICATION_TOKENS.CREATED_AT, now)
        .onConflict(EMAIL_VERIFICATION_TOKENS.USER_ID)
        .doUpdate()
        .set(EMAIL_VERIFICATION_TOKENS.EMAIL, email)
        .set(EMAIL_VERIFICATION_TOKENS.TOKEN_HASH, tokenHash)
        .set(EMAIL_VERIFICATION_TOKENS.EXPIRES_AT, expiresAt)
        .set(EMAIL_VERIFICATION_TOKENS.CREATED_AT, now)
        .execute();
  }

  /**
   * トークンの行を消して、消した行を返す（1回だけ使える）。読むのと消すのを1つの SQL で行うので、同じリンクが同時に2回開かれても、取り出せるのは一方だけ。
   * 期限切れかどうかは呼ぶ側で確かめる（期限切れの行も、取り出した時点で消える）
   */
  public Optional<EmailVerificationTokensRecord> takeByTokenHash(String tokenHash) {
    return dsl.deleteFrom(EMAIL_VERIFICATION_TOKENS)
        .where(EMAIL_VERIFICATION_TOKENS.TOKEN_HASH.eq(tokenHash))
        .returning()
        .fetchOptional();
  }

  /** 期限切れ（期限ちょうどを含む）の行を消し、消した件数を返す */
  public int deleteExpired(OffsetDateTime now) {
    return dsl.deleteFrom(EMAIL_VERIFICATION_TOKENS)
        .where(EMAIL_VERIFICATION_TOKENS.EXPIRES_AT.le(now))
        .execute();
  }
}
