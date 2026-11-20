package com.okimoto.fitlog.auth.repository;

import static com.okimoto.fitlog.jooq.Tables.REFRESH_TOKENS;

import com.okimoto.fitlog.jooq.tables.records.RefreshTokensRecord;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/** リフレッシュトークン（refresh_tokens）の読み書き。日時はすべて引数で受け取る（SQL の now() を使わない） */
@Repository
public class RefreshTokenRepository {

  private final DSLContext dsl;

  public RefreshTokenRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public void insert(long userId, String tokenHash, OffsetDateTime expiresAt, OffsetDateTime now) {
    dsl.insertInto(REFRESH_TOKENS)
        .set(REFRESH_TOKENS.USER_ID, userId)
        .set(REFRESH_TOKENS.TOKEN_HASH, tokenHash)
        .set(REFRESH_TOKENS.EXPIRES_AT, expiresAt)
        .set(REFRESH_TOKENS.CREATED_AT, now)
        .execute();
  }

  public Optional<RefreshTokensRecord> findByTokenHash(String tokenHash) {
    return dsl.selectFrom(REFRESH_TOKENS)
        .where(REFRESH_TOKENS.TOKEN_HASH.eq(tokenHash))
        .fetchOptional();
  }

  /** 有効なトークンを、交換済み（無効・交換の日時あり）にする。ほかのリクエストが先に交換していれば何もしない */
  public boolean rotate(long id, OffsetDateTime now) {
    return dsl.update(REFRESH_TOKENS)
            .set(REFRESH_TOKENS.REVOKED_AT, now)
            .set(REFRESH_TOKENS.ROTATED_AT, now)
            .where(REFRESH_TOKENS.ID.eq(id))
            .and(REFRESH_TOKENS.REVOKED_AT.isNull())
            .execute()
        == 1;
  }

  /** トークンを無効にする（ログアウト）。すでに無効なら何もしない */
  public void revoke(long id, OffsetDateTime now) {
    dsl.update(REFRESH_TOKENS)
        .set(REFRESH_TOKENS.REVOKED_AT, now)
        .where(REFRESH_TOKENS.ID.eq(id))
        .and(REFRESH_TOKENS.REVOKED_AT.isNull())
        .execute();
  }

  /** そのユーザーの有効なトークンを、すべて無効にする（使い回しの検知） */
  public void revokeAllByUserId(long userId, OffsetDateTime now) {
    dsl.update(REFRESH_TOKENS)
        .set(REFRESH_TOKENS.REVOKED_AT, now)
        .where(REFRESH_TOKENS.USER_ID.eq(userId))
        .and(REFRESH_TOKENS.REVOKED_AT.isNull())
        .execute();
  }

  /** 期限切れ（期限ちょうどを含む）の行を消し、消した件数を返す */
  public int deleteExpired(OffsetDateTime now) {
    return dsl.deleteFrom(REFRESH_TOKENS).where(REFRESH_TOKENS.EXPIRES_AT.le(now)).execute();
  }
}
