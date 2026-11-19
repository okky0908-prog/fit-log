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
  private final Clock clock;

  /** 登録されていないメールアドレスでログインされたときに照合に使う、ダミーのハッシュ */
  private final String dummyPasswordHash;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      Clock clock) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.clock = clock;
    this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
  }

  /**
   * 新規登録（A-01）。入力の形は Controller で確かめ済み。
   *
   * <p>保存は1回の INSERT なので、トランザクションは使わない。BCrypt のハッシュ（重い計算）の間に DB の接続を持たないようにするため
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
            passwordEncoder.encode(password),
            OffsetDateTime.now(clock));
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
    return new AuthResult(jwtService.issue(saved.getId()), saved);
  }

  /**
   * ログイン（A-02）。どちらが違うかは教えない。
   *
   * <p>登録されていないメールアドレスでも、ダミーのハッシュで同じように照合する。応答の速さから、登録されているメールアドレスを推測されないようにするため
   */
  public AuthResult login(String email, String password) {
    Optional<UsersRecord> user = userRepository.findByEmail(normalizeEmail(email));
    String hash = user.map(UsersRecord::getPasswordHash).orElse(dummyPasswordHash);
    // BCrypt は72バイトを超えるパスワードで例外を投げる。登録できない長さなので、照合せずに「合わない」とする
    boolean matches = fitsBcrypt(password) && passwordEncoder.matches(password, hash);
    if (user.isEmpty() || !matches) {
      throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
    }
    return new AuthResult(jwtService.issue(user.get().getId()), user.get());
  }

  /** ログイン中のユーザー（A-03）。トークンは正しいが、ユーザーがいない（退会した）ときは 401 */
  public UsersRecord me(long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
  }

  private static boolean fitsBcrypt(String password) {
    return password.getBytes(StandardCharsets.UTF_8).length <= PASSWORD_MAX_BYTES;
  }

  /** メールアドレスは前後の空白を除き、小文字にそろえる（docs/api.md A-01） */
  private static String normalizeEmail(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }
}
