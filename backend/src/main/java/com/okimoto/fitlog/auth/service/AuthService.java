package com.okimoto.fitlog.auth.service;

import com.okimoto.fitlog.common.controller.ApiError;
import com.okimoto.fitlog.common.controller.ApiException;
import com.okimoto.fitlog.common.controller.ErrorCode;
import com.okimoto.fitlog.jooq.tables.records.UsersRecord;
import com.okimoto.fitlog.user.repository.NewUser;
import com.okimoto.fitlog.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/** 新規登録・ログイン・ログイン中のユーザー（A-01〜A-03） */
@Service
public class AuthService {

  static final String USERNAME_RESERVED = "このユーザー名は使えません";
  static final String USERNAME_TAKEN = "このユーザー名はすでに使われています";
  static final String EMAIL_TAKEN = "このメールアドレスはすでに登録されています";

  /**
   * 使えないユーザー名（小文字）。API の URL とぶつかるもの（/api/users/search はユーザー検索なので、search という人のプロフィールを取れなくなる）。
   * 4文字未満の名前（me など）は、もともとユーザー名の形で使えない
   */
  static final Set<String> RESERVED_USERNAMES = Set.of("search");

  /** メールアドレスの一意制約の名前（V1 のマイグレーションで UNIQUE にした列。PostgreSQL が付ける名前） */
  private static final String EMAIL_UNIQUE_CONSTRAINT = "users_email_key";

  /** BCrypt が扱えるのは72バイトまで */
  private static final int PASSWORD_MAX_BYTES = 72;

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final RefreshTokenService refreshTokenService;
  private final LoginAttemptLimiter loginAttemptLimiter;
  private final PasswordHashingGate passwordHashingGate;
  private final TransactionOperations transactionOperations;
  private final Clock clock;

  /** 登録されていないメールアドレスでログインされたときに照合に使う、ダミーのハッシュ */
  private final String dummyPasswordHash;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      RefreshTokenService refreshTokenService,
      LoginAttemptLimiter loginAttemptLimiter,
      PasswordHashingGate passwordHashingGate,
      TransactionOperations transactionOperations,
      Clock clock) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.refreshTokenService = refreshTokenService;
    this.loginAttemptLimiter = loginAttemptLimiter;
    this.passwordHashingGate = passwordHashingGate;
    this.transactionOperations = transactionOperations;
    this.clock = clock;
    this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
  }

  /**
   * 新規登録（A-01）。入力の形は Controller で確かめ済み。
   *
   * <p>BCrypt のハッシュ（重い計算）はトランザクションの外で行い、DB の接続を持つのは保存の間だけにする。ユーザーとリフレッシュトークンの保存は、1つのトランザクションで確定する
   */
  public AuthResult signup(String username, String displayName, String email, String password) {
    String normalizedEmail = normalizeEmail(email);
    if (RESERVED_USERNAMES.contains(username.toLowerCase(Locale.ROOT))) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED,
          List.of(new ApiError.FieldError("username", USERNAME_RESERVED)));
    }

    List<ApiError.FieldError> conflicts = new ArrayList<>();
    if (userRepository.existsByUsernameIgnoreCase(username)) {
      conflicts.add(new ApiError.FieldError("username", USERNAME_TAKEN));
    }
    if (userRepository.existsByEmail(normalizedEmail)) {
      conflicts.add(new ApiError.FieldError("email", EMAIL_TAKEN));
    }
    if (!conflicts.isEmpty()) {
      throw new ApiException(ErrorCode.ALREADY_REGISTERED, conflicts);
    }

    NewUser user =
        new NewUser(
            username,
            displayName.strip(),
            normalizedEmail,
            passwordHashingGate.run(() -> passwordEncoder.encode(password)),
            OffsetDateTime.now(clock));
    return transactionOperations.execute(status -> saveAndIssueTokens(user));
  }

  private AuthResult saveAndIssueTokens(NewUser user) {
    UsersRecord saved;
    try {
      saved = userRepository.insert(user);
    } catch (DuplicateKeyException e) {
      // 上で確かめたあと、同時に同じ内容で登録された。DB の一意制約で止まったものを 409 にする（docs/test-plan.md 4.6）
      boolean emailTaken = String.valueOf(e.getMessage()).contains(EMAIL_UNIQUE_CONSTRAINT);
      throw new ApiException(
          ErrorCode.ALREADY_REGISTERED,
          List.of(
              emailTaken
                  ? new ApiError.FieldError("email", EMAIL_TAKEN)
                  : new ApiError.FieldError("username", USERNAME_TAKEN)));
    }
    return issueTokens(saved);
  }

  /**
   * ログイン（A-02）。どちらが違うかは教えない。
   *
   * <ul>
   *   <li>失敗が続いてロック中なら、ユーザーを探さず、パスワードも照合しない（429）
   *   <li>登録されていないメールアドレスでも、ダミーのハッシュで同じように照合する。応答の速さから、登録されているメールアドレスを推測されないようにするため
   *   <li>トランザクションは使わない。照合の間に DB の接続を持たないようにするため（DB の操作はユーザーの検索とトークンの保存の1回ずつ）
   * </ul>
   */
  public AuthResult login(String email, String password) {
    String normalizedEmail = normalizeEmail(email);
    loginAttemptLimiter.checkNotLocked(normalizedEmail);
    Optional<UsersRecord> user = userRepository.findByEmail(normalizedEmail);
    String hash = user.map(UsersRecord::getPasswordHash).orElse(dummyPasswordHash);
    // BCrypt は72バイトより後ろを無視して照合する。72バイトを超えるパスワードは登録できないので、照合せずに「合わない」とする
    boolean matches =
        fitsBcrypt(password)
            && passwordHashingGate.run(() -> passwordEncoder.matches(password, hash));
    if (user.isEmpty() || !matches) {
      loginAttemptLimiter.recordFailure(normalizedEmail);
      throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
    }
    loginAttemptLimiter.recordSuccess(normalizedEmail);
    return issueTokens(user.get());
  }

  /**
   * アクセストークンの再発行（A-04）。リフレッシュトークンも新しいものに交換する。
   *
   * <p>トランザクションは {@link RefreshTokenService#rotate}
   * の中で終える（使えないトークンで投げる例外で、使い回しの検知の処理がロールバックされないように）
   */
  public AuthResult refresh(String refreshToken) {
    RefreshTokenService.Rotation rotation =
        refreshTokenService
            .rotate(refreshToken)
            .orElseThrow(() -> new ApiException(ErrorCode.SESSION_EXPIRED));
    UsersRecord user =
        userRepository
            .findById(rotation.userId())
            .orElseThrow(() -> new ApiException(ErrorCode.SESSION_EXPIRED));
    return new AuthResult(jwtService.issue(user.getId()), rotation.refreshToken(), user);
  }

  /** ログアウト（A-05）。リフレッシュトークンを無効にする（アクセストークンは、期限の15分までは使えるまま） */
  public void logout(String refreshToken) {
    refreshTokenService.revoke(refreshToken);
  }

  /** ログイン中のユーザー（A-03）。トークンは正しいが、ユーザーがいない（退会した）ときは 401 */
  public UsersRecord me(long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
  }

  private AuthResult issueTokens(UsersRecord user) {
    return new AuthResult(
        jwtService.issue(user.getId()), refreshTokenService.issue(user.getId()), user);
  }

  private static boolean fitsBcrypt(String password) {
    return password.getBytes(StandardCharsets.UTF_8).length <= PASSWORD_MAX_BYTES;
  }

  /** メールアドレスは前後の空白を除き、小文字にそろえる（docs/api.md A-01） */
  private static String normalizeEmail(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }
}
