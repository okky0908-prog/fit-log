package com.okimoto.fitlog.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.okimoto.fitlog.jooq.tables.records.RefreshTokensRecord;
import com.okimoto.fitlog.support.TestcontainersConfiguration;
import com.okimoto.fitlog.user.repository.NewUser;
import com.okimoto.fitlog.user.repository.UserRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** リフレッシュトークンの読み書き。本物の DB で確かめる（テストごとにロールバックする） */
@JooqTest
@Import({TestcontainersConfiguration.class, RefreshTokenRepository.class, UserRepository.class})
@ActiveProfiles("test")
class RefreshTokenRepositoryTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-20T10:00:00+09:00");
  private static final OffsetDateTime EXPIRES_AT = NOW.plusDays(14);

  @Autowired private RefreshTokenRepository repository;
  @Autowired private UserRepository userRepository;

  private long userId;
  private long otherUserId;

  @BeforeEach
  void setUp() {
    userId = userRepository.insert(newUser("yamada")).getId();
    otherUserId = userRepository.insert(newUser("sato")).getId();
  }

  @Test
  void 保存したトークンをハッシュで探せる() {
    repository.insert(userId, hash('a'), EXPIRES_AT, NOW);

    RefreshTokensRecord found = repository.findByTokenHash(hash('a')).orElseThrow();
    assertThat(found.getUserId()).isEqualTo(userId);
    assertThat(found.getExpiresAt()).isEqualTo(EXPIRES_AT);
    assertThat(found.getRevokedAt()).isNull();
    assertThat(found.getRotatedAt()).isNull();
    assertThat(repository.findByTokenHash(hash('b'))).isEmpty();
  }

  @Test
  void 交換すると無効と交換の日時が入り_2回目の交換はできない() {
    repository.insert(userId, hash('a'), EXPIRES_AT, NOW);
    long id = repository.findByTokenHash(hash('a')).orElseThrow().getId();

    assertThat(repository.rotate(id, NOW.plusMinutes(1))).isTrue();
    // ほかのリクエストが先に交換した場合と同じ
    assertThat(repository.rotate(id, NOW.plusMinutes(2))).isFalse();

    RefreshTokensRecord rotated = repository.findByTokenHash(hash('a')).orElseThrow();
    assertThat(rotated.getRevokedAt()).isEqualTo(NOW.plusMinutes(1));
    assertThat(rotated.getRotatedAt()).isEqualTo(NOW.plusMinutes(1));
  }

  @Test
  void ログアウトでは無効の日時だけが入り_交換の日時は入らない() {
    repository.insert(userId, hash('a'), EXPIRES_AT, NOW);
    long id = repository.findByTokenHash(hash('a')).orElseThrow().getId();

    repository.revoke(id, NOW.plusMinutes(1));
    repository.revoke(id, NOW.plusMinutes(2));

    RefreshTokensRecord revoked = repository.findByTokenHash(hash('a')).orElseThrow();
    // 2回目は何もしない（最初に無効にした日時のまま）
    assertThat(revoked.getRevokedAt()).isEqualTo(NOW.plusMinutes(1));
    assertThat(revoked.getRotatedAt()).isNull();
  }

  @Test
  void そのユーザーの有効なトークンだけをすべて無効にする() {
    repository.insert(userId, hash('a'), EXPIRES_AT, NOW);
    repository.insert(userId, hash('b'), EXPIRES_AT, NOW);
    repository.insert(otherUserId, hash('c'), EXPIRES_AT, NOW);
    long rotatedId = repository.findByTokenHash(hash('b')).orElseThrow().getId();
    repository.rotate(rotatedId, NOW.plusMinutes(1));

    repository.revokeAllByUserId(userId, NOW.plusMinutes(5));

    assertThat(repository.findByTokenHash(hash('a')).orElseThrow().getRevokedAt())
        .isEqualTo(NOW.plusMinutes(5));
    // すでに無効だったものは、最初に無効にした日時のまま
    assertThat(repository.findByTokenHash(hash('b')).orElseThrow().getRevokedAt())
        .isEqualTo(NOW.plusMinutes(1));
    // ほかのユーザーのトークンは有効なまま
    assertThat(repository.findByTokenHash(hash('c')).orElseThrow().getRevokedAt()).isNull();
  }

  @Test
  void 期限ちょうどを含む期限切れの行だけを消す() {
    repository.insert(userId, hash('a'), NOW.minusSeconds(1), NOW.minusDays(14));
    repository.insert(userId, hash('b'), NOW, NOW.minusDays(14));
    repository.insert(userId, hash('c'), NOW.plusSeconds(1), NOW.minusDays(14));

    assertThat(repository.deleteExpired(NOW)).isEqualTo(2);

    assertThat(repository.findByTokenHash(hash('a'))).isEmpty();
    assertThat(repository.findByTokenHash(hash('b'))).isEmpty();
    assertThat(repository.findByTokenHash(hash('c'))).isPresent();
  }

  /** SHA-256 のハッシュの代わり（16進数64文字） */
  private static String hash(char c) {
    return String.valueOf(c).repeat(64);
  }

  private static NewUser newUser(String username) {
    return new NewUser(username, "表示名", username + "@example.com", "hash", NOW);
  }
}
