package com.okimoto.fitlog.auth.service;

import com.okimoto.fitlog.auth.config.RefreshTokenProperties;
import com.okimoto.fitlog.auth.repository.RefreshTokenRepository;
import com.okimoto.fitlog.jooq.tables.records.RefreshTokensRecord;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * リフレッシュトークンの発行・交換（ローテーション）・無効化（前回の SNS アプリと同じ）。
 *
 * <ul>
 *   <li>トークンは推測できないランダムな文字列（256ビット）。DB には SHA-256 のハッシュだけを保存する
 *   <li>使うたびに古いものを無効にして、新しいものを発行する（ローテーション）
 *   <li>無効になったトークンがもう一度使われたら、盗まれて使い回された可能性があるので、そのユーザーのトークンをすべて無効にする
 *   <li>ただし、交換してから猶予の時間（10秒）以内の再利用は、2つのタブが同時に再発行した・再発行の最中にリロードした などの 「同時のリクエスト」とみなして、新しいトークンを発行する
 * </ul>
 */
@Service
public class RefreshTokenService {

  private final RefreshTokenRepository repository;
  private final RefreshTokenProperties properties;
  private final Clock clock;

  public RefreshTokenService(
      RefreshTokenRepository repository, RefreshTokenProperties properties, Clock clock) {
    this.repository = repository;
    this.properties = properties;
    this.clock = clock;
  }

  /** 新しいトークンを発行し、トークンそのもの（ブラウザに渡す値）を返す */
  public String issue(long userId) {
    String token = OpaqueTokens.generate();
    OffsetDateTime now = OffsetDateTime.now(clock);
    repository.insert(userId, hash(token), now.plus(properties.expiration()), now);
    return token;
  }

  /**
   * 有効なトークンなら無効にして、新しいものと交換する。
   *
   * <p>例外ではなく空を返す。使い回しを検知したときの「すべて無効にする」処理を、ロールバックさせずに確定させるため
   *
   * @return 交換できたときは、ユーザーの ID と新しいトークン。無効・期限切れ・使い回しのときは空
   */
  @Transactional
  public Optional<Rotation> rotate(String token) {
    Optional<RefreshTokensRecord> found = repository.findByTokenHash(hash(token));
    if (found.isEmpty()) {
      return Optional.empty();
    }
    RefreshTokensRecord current = found.get();
    OffsetDateTime now = OffsetDateTime.now(clock);
    if (current.getRevokedAt() != null) {
      return reuseOfRevoked(current, now);
    }
    // 期限ちょうども使えない
    if (!current.getExpiresAt().isAfter(now)) {
      return Optional.empty();
    }
    if (!repository.rotate(current.getId(), now)) {
      // 読んでから交換するまでの間に、ほかのリクエストが先に交換した
      return repository
          .findByTokenHash(current.getTokenHash())
          .flatMap(latest -> reuseOfRevoked(latest, now));
    }
    return Optional.of(new Rotation(current.getUserId(), issue(current.getUserId())));
  }

  /**
   * すでに無効なトークンが使われたとき。交換してから猶予の時間以内（期限内）なら、同時のリクエストとして新しいトークンを発行する。
   * それ以外（ログアウト済み・使い回しの検知で無効にした・猶予を過ぎた）は、盗まれた可能性があるので、そのユーザーのトークンをすべて無効にする
   */
  private Optional<Rotation> reuseOfRevoked(RefreshTokensRecord token, OffsetDateTime now) {
    OffsetDateTime rotatedAt = token.getRotatedAt();
    boolean concurrentRequest =
        rotatedAt != null
            && !now.isAfter(rotatedAt.plus(properties.reuseGrace()))
            && token.getExpiresAt().isAfter(now);
    if (concurrentRequest) {
      return Optional.of(new Rotation(token.getUserId(), issue(token.getUserId())));
    }
    repository.revokeAllByUserId(token.getUserId(), now);
    return Optional.empty();
  }

  /** ログアウト。知らないトークンなら何もしない */
  public void revoke(String token) {
    repository
        .findByTokenHash(hash(token))
        .ifPresent(found -> repository.revoke(found.getId(), OffsetDateTime.now(clock)));
  }

  /**
   * 期限切れ（期限ちょうどを含む）の行を消す（{@link DailyCleanupJob} が毎日呼ぶ）。無効にした行も、期限が切れたら消す（期限内のものは、使い回しの検知に使うので残す）。
   * 消したあとで期限切れのトークンが使われても、「知らないトークン」として使えないだけ（全無効化は起きない）
   *
   * @return 消した件数
   */
  public int deleteExpired() {
    return repository.deleteExpired(OffsetDateTime.now(clock));
  }

  /** トークンの SHA-256 のハッシュ（16進数64文字） */
  static String hash(String token) {
    return OpaqueTokens.hash(token);
  }

  /**
   * 交換の結果。
   *
   * @param userId トークンの持ち主
   * @param refreshToken 新しいリフレッシュトークン（ブラウザに渡す値）
   */
  public record Rotation(long userId, String refreshToken) {}
}
