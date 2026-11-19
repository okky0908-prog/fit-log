package com.okimoto.fitlog.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.okimoto.fitlog.jooq.tables.records.UsersRecord;
import com.okimoto.fitlog.support.TestcontainersConfiguration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;

/** ユーザーの読み書き。本物の DB で確かめる（テストごとにロールバックする） */
@JooqTest
@Import({TestcontainersConfiguration.class, UserRepository.class})
@ActiveProfiles("test")
class UserRepositoryTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-20T10:00:00+09:00");

  @Autowired private UserRepository userRepository;

  @Test
  void 保存すると_IDと既定値の入った行を返す() {
    UsersRecord saved = userRepository.insert(newUser("yamada", "yamada@example.com"));

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getUsername()).isEqualTo("yamada");
    assertThat(saved.getEmailVerifiedAt()).isNull();
    assertThat(saved.getIsPrivate()).isFalse();
    assertThat(saved.getWeightUnit()).isEqualTo("kg");
    assertThat(saved.getDistanceUnit()).isEqualTo("km");
    assertThat(saved.getCreatedAt()).isEqualTo(NOW);
    assertThat(saved.getUpdatedAt()).isEqualTo(NOW);
  }

  @Test
  void ユーザー名は大文字小文字を区別せずに使われているかを判定する() {
    userRepository.insert(newUser("yamada", "yamada@example.com"));

    assertThat(userRepository.existsByUsernameIgnoreCase("YAMADA")).isTrue();
    assertThat(userRepository.existsByUsernameIgnoreCase("yamada2")).isFalse();
  }

  @Test
  void メールアドレスが使われているかを判定する() {
    userRepository.insert(newUser("yamada", "yamada@example.com"));

    assertThat(userRepository.existsByEmail("yamada@example.com")).isTrue();
    assertThat(userRepository.existsByEmail("sato@example.com")).isFalse();
  }

  @Test
  void メールアドレスとIDで探せる() {
    UsersRecord saved = userRepository.insert(newUser("yamada", "yamada@example.com"));

    assertThat(userRepository.findByEmail("yamada@example.com"))
        .hasValueSatisfying(user -> assertThat(user.getId()).isEqualTo(saved.getId()));
    assertThat(userRepository.findById(saved.getId()))
        .hasValueSatisfying(user -> assertThat(user.getUsername()).isEqualTo("yamada"));
    assertThat(userRepository.findByEmail("sato@example.com")).isEmpty();
    assertThat(userRepository.findById(saved.getId() + 1)).isEmpty();
  }

  /** AuthService は、同時の登録で止まったときの項目を、例外のメッセージの制約の名前で見分ける */
  @Test
  void 大文字だけ違うユーザー名はDBの一意制約で止まり_制約の名前が分かる() {
    userRepository.insert(newUser("yamada", "yamada@example.com"));

    assertThatThrownBy(() -> userRepository.insert(newUser("YAMADA", "other@example.com")))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining("uq_users_username_lower");
  }

  @Test
  void 同じメールアドレスはDBの一意制約で止まり_制約の名前が分かる() {
    userRepository.insert(newUser("yamada", "yamada@example.com"));

    assertThatThrownBy(() -> userRepository.insert(newUser("sato", "yamada@example.com")))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining("users_email_key");
  }

  private static NewUser newUser(String username, String email) {
    return new NewUser(username, "表示名", email, "hash", NOW);
  }
}
