package com.okimoto.fitlog.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.okimoto.fitlog.jooq.tables.records.EmailVerificationTokensRecord;
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

/** メールアドレスの確認用のトークンの読み書き。本物の DB で確かめる（テストごとにロールバックする） */
@JooqTest
@Import({
  TestcontainersConfiguration.class,
  EmailVerificationTokenRepository.class,
  UserRepository.class
})
@ActiveProfiles("test")
class EmailVerificationTokenRepositoryTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-20T10:00:00+09:00");
  private static final OffsetDateTime EXPIRES_AT = NOW.plusHours(24);

  @Autowired private EmailVerificationTokenRepository repository;
  @Autowired private UserRepository userRepository;

  private long userId;
  private long otherUserId;

  @BeforeEach
  void setUp() {
    userId = userRepository.insert(newUser("yamada")).getId();
    otherUserId = userRepository.insert(newUser("sato")).getId();
  }

  @Test
  void 保存したトークンをハッシュで取り出すと_行が消える() {
    repository.upsert(userId, "yamada@example.com", hash('a'), EXPIRES_AT, NOW);

    EmailVerificationTokensRecord taken = repository.takeByTokenHash(hash('a')).orElseThrow();

    assertThat(taken.getUserId()).isEqualTo(userId);
    assertThat(taken.getEmail()).isEqualTo("yamada@example.com");
    assertThat(taken.getExpiresAt()).isEqualTo(EXPIRES_AT);
    // 1回だけ使える：もう一度取り出せない
    assertThat(repository.takeByTokenHash(hash('a'))).isEmpty();
  }

  @Test
  void 知らないハッシュは取り出せない() {
    repository.upsert(userId, "yamada@example.com", hash('a'), EXPIRES_AT, NOW);

    assertThat(repository.takeByTokenHash(hash('b'))).isEmpty();
  }

  @Test
  void 同じユーザーにもう一度保存すると上書きされ_前のトークンは使えない() {
    repository.upsert(userId, "yamada@example.com", hash('a'), EXPIRES_AT, NOW);
    repository.upsert(
        userId, "yamada@example.com", hash('b'), EXPIRES_AT.plusHours(1), NOW.plusHours(1));

    assertThat(repository.takeByTokenHash(hash('a'))).isEmpty();
    EmailVerificationTokensRecord taken = repository.takeByTokenHash(hash('b')).orElseThrow();
    assertThat(taken.getExpiresAt()).isEqualTo(EXPIRES_AT.plusHours(1));
    assertThat(taken.getCreatedAt()).isEqualTo(NOW.plusHours(1));
  }

  @Test
  void ほかのユーザーのトークンは上書きしない() {
    repository.upsert(userId, "yamada@example.com", hash('a'), EXPIRES_AT, NOW);
    repository.upsert(otherUserId, "sato@example.com", hash('b'), EXPIRES_AT, NOW);

    assertThat(repository.takeByTokenHash(hash('a'))).isPresent();
    assertThat(repository.takeByTokenHash(hash('b'))).isPresent();
  }

  @Test
  void 期限ちょうどを含む期限切れの行だけを消す() {
    repository.upsert(userId, "yamada@example.com", hash('a'), NOW, NOW.minusHours(24));
    repository.upsert(otherUserId, "sato@example.com", hash('b'), NOW.plusSeconds(1), NOW);

    assertThat(repository.deleteExpired(NOW)).isEqualTo(1);

    assertThat(repository.takeByTokenHash(hash('a'))).isEmpty();
    assertThat(repository.takeByTokenHash(hash('b'))).isPresent();
  }

  private static String hash(char c) {
    return String.valueOf(c).repeat(64);
  }

  private static NewUser newUser(String username) {
    return new NewUser(username, username, username + "@example.com", "hash", NOW);
  }
}
