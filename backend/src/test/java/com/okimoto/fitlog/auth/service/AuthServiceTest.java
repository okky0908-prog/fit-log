package com.okimoto.fitlog.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.okimoto.fitlog.auth.config.JwtProperties;
import com.okimoto.fitlog.common.controller.ApiError;
import com.okimoto.fitlog.common.controller.ApiException;
import com.okimoto.fitlog.jooq.tables.records.UsersRecord;
import com.okimoto.fitlog.user.repository.NewUser;
import com.okimoto.fitlog.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 新規登録・ログイン・ログイン中のユーザー（A-01〜A-03）の処理。Repository は偽物 */
class AuthServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-20T01:00:00Z");

  private final UserRepository userRepository = mock(UserRepository.class);
  // テストを速くするため、ハッシュの強さを最低（4）にする
  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final JwtService jwtService =
      new JwtService(
          new JwtProperties("test-secret-for-jwt-signing-0123456789abcdef", Duration.ofMinutes(15)),
          clock);
  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService = new AuthService(userRepository, passwordEncoder, jwtService, clock);
  }

  @Test
  void 新規登録では_メールアドレスを小文字にそろえ_表示名の前後の空白を除き_パスワードをハッシュにして保存する() {
    given(userRepository.insert(any())).willAnswer(invocation -> saved(invocation.getArgument(0)));

    AuthResult result =
        authService.signup("Yamada", "  山田太郎  ", "  Yamada@Example.COM ", "password123");

    ArgumentCaptor<NewUser> captor = ArgumentCaptor.forClass(NewUser.class);
    verify(userRepository).insert(captor.capture());
    NewUser user = captor.getValue();
    assertThat(user.username()).isEqualTo("Yamada");
    assertThat(user.displayName()).isEqualTo("山田太郎");
    assertThat(user.email()).isEqualTo("yamada@example.com");
    assertThat(passwordEncoder.matches("password123", user.passwordHash())).isTrue();
    assertThat(user.createdAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
    // 保存したユーザーのトークンを返す（登録のあとはそのままログイン状態）
    assertThat(jwtService.parseUserId(result.accessToken())).contains(1L);
    assertThat(result.user().getId()).isEqualTo(1L);
  }

  @ParameterizedTest
  @ValueSource(strings = {"search", "Search", "SEARCH"})
  void ユーザー検索のURLとぶつかるユーザー名は使えない(String username) {
    assertThatThrownBy(
            () -> authService.signup(username, "山田太郎", "yamada@example.com", "password123"))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.toApiError().code()).isEqualTo("VALIDATION_FAILED");
              assertThat(e.toApiError().errors())
                  .containsExactly(new ApiError.FieldError("username", "このユーザー名は使えません"));
            });
    verify(userRepository, never()).insert(any());
  }

  @Test
  void ユーザー名とメールアドレスが両方使われていれば_両方の項目を409で返す() {
    given(userRepository.existsByUsernameIgnoreCase("yamada")).willReturn(true);
    given(userRepository.existsByEmail("yamada@example.com")).willReturn(true);

    assertThatThrownBy(
            () -> authService.signup("yamada", "山田太郎", "Yamada@example.com", "password123"))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.toApiError().code()).isEqualTo("ALREADY_REGISTERED");
              assertThat(e.toApiError().errors())
                  .containsExactly(
                      new ApiError.FieldError("username", "このユーザー名はすでに使われています"),
                      new ApiError.FieldError("email", "このメールアドレスはすでに登録されています"));
            });
    verify(userRepository, never()).insert(any());
  }

  @Test
  void 確かめたあとに同時に同じユーザー名で登録されたら_DBの一意制約で止まったものを409にする() {
    given(userRepository.insert(any()))
        .willThrow(
            new DuplicateKeyException(
                "duplicate key value violates unique constraint \"uq_users_username_lower\""));

    assertThatThrownBy(
            () -> authService.signup("yamada", "山田太郎", "yamada@example.com", "password123"))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.toApiError().code()).isEqualTo("ALREADY_REGISTERED");
              assertThat(e.toApiError().errors())
                  .containsExactly(new ApiError.FieldError("username", "このユーザー名はすでに使われています"));
            });
  }

  @Test
  void 確かめたあとに同時に同じメールアドレスで登録されたら_DBの一意制約で止まったものを409にする() {
    given(userRepository.insert(any()))
        .willThrow(
            new DuplicateKeyException(
                "duplicate key value violates unique constraint \"users_email_key\""));

    assertThatThrownBy(
            () -> authService.signup("yamada", "山田太郎", "yamada@example.com", "password123"))
        .isInstanceOfSatisfying(
            ApiException.class,
            e ->
                assertThat(e.toApiError().errors())
                    .containsExactly(new ApiError.FieldError("email", "このメールアドレスはすでに登録されています")));
  }

  @Test
  void メールアドレスの大文字小文字と前後の空白が違っても_パスワードが合えばログインできる() {
    given(userRepository.findByEmail("yamada@example.com")).willReturn(Optional.of(registered()));

    AuthResult result = authService.login(" Yamada@Example.com ", "password123");

    assertThat(jwtService.parseUserId(result.accessToken())).contains(1L);
    assertThat(result.user().getUsername()).isEqualTo("yamada");
  }

  @Test
  void パスワードが違えば401のINVALID_CREDENTIALS() {
    given(userRepository.findByEmail("yamada@example.com")).willReturn(Optional.of(registered()));

    assertInvalidCredentials(() -> authService.login("yamada@example.com", "wrong-password"));
  }

  @Test
  void 登録されていないメールアドレスでも_同じ401のINVALID_CREDENTIALS() {
    given(userRepository.findByEmail("nobody@example.com")).willReturn(Optional.empty());

    assertInvalidCredentials(() -> authService.login("nobody@example.com", "password123"));
  }

  /**
   * BCrypt は72バイトより後ろを無視して照合する。72バイトのパスワードで登録した人に、後ろに文字を足したパスワードでログインできてはいけない。
   * 72バイトを超えるパスワードは登録できないので、照合せずに「合わない」とする
   */
  @Test
  void 登録した72バイトのパスワードの後ろに文字を足したものではログインできない() {
    String password = "あ".repeat(24);
    given(userRepository.findByEmail("yamada@example.com"))
        .willReturn(Optional.of(registered(password)));

    assertInvalidCredentials(() -> authService.login("yamada@example.com", password + "x"));
  }

  @Test
  void 七十二バイトのパスワードそのものではログインできる() {
    String password = "あ".repeat(24);
    given(userRepository.findByEmail("yamada@example.com"))
        .willReturn(Optional.of(registered(password)));

    assertThat(authService.login("yamada@example.com", password).user().getUsername())
        .isEqualTo("yamada");
  }

  @Test
  void ログイン中のユーザーを返す() {
    given(userRepository.findById(1L)).willReturn(Optional.of(registered()));

    assertThat(authService.me(1L).getUsername()).isEqualTo("yamada");
  }

  /** トークンは正しいが、ユーザーがいない（退会した）とき。画面はログイン画面に戻る */
  @Test
  void トークンのユーザーがいなければ401のUNAUTHENTICATED() {
    given(userRepository.findById(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> authService.me(1L))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.toApiError().code()).isEqualTo("UNAUTHENTICATED"));
  }

  private static void assertInvalidCredentials(ThrowingCallable login) {
    assertThatThrownBy(login)
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.toApiError().code()).isEqualTo("INVALID_CREDENTIALS"));
  }

  /** 登録済みのユーザー（パスワードは password123） */
  private UsersRecord registered() {
    return registered("password123");
  }

  private UsersRecord registered(String password) {
    return saved(
        new NewUser(
            "yamada",
            "山田太郎",
            "yamada@example.com",
            passwordEncoder.encode(password),
            OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC)));
  }

  /** DB に保存した行（ID・既定値が入る）の代わり */
  private static UsersRecord saved(NewUser user) {
    UsersRecord record = new UsersRecord();
    record.setId(1L);
    record.setUsername(user.username());
    record.setDisplayName(user.displayName());
    record.setEmail(user.email());
    record.setPasswordHash(user.passwordHash());
    record.setIsPrivate(false);
    record.setWeightUnit("kg");
    record.setDistanceUnit("km");
    record.setCreatedAt(user.createdAt());
    record.setUpdatedAt(user.createdAt());
    return record;
  }
}
