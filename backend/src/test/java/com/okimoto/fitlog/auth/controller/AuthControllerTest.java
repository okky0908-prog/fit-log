package com.okimoto.fitlog.auth.controller;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.okimoto.fitlog.auth.config.SecurityConfig;
import com.okimoto.fitlog.auth.service.AuthResult;
import com.okimoto.fitlog.auth.service.AuthService;
import com.okimoto.fitlog.auth.service.JwtService;
import com.okimoto.fitlog.common.config.ClockConfig;
import com.okimoto.fitlog.common.controller.ApiException;
import com.okimoto.fitlog.common.controller.ErrorCode;
import com.okimoto.fitlog.jooq.tables.records.UsersRecord;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/** 認証の API（A-01〜A-03）の入口。Service は偽物、認証の設定は本物（docs/test-plan.md 3.1） */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtService.class, ClockConfig.class})
@ActiveProfiles("test")
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtService jwtService;
  @MockitoBean private AuthService authService;

  @Test
  void 新規登録できたら201とトークンとユーザーの情報を返す() throws Exception {
    given(authService.signup("yamada", "山田太郎", "yamada@example.com", "password123"))
        .willReturn(new AuthResult("access-token", "refresh-token", user()));

    mockMvc
        .perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"username": "yamada", "displayName": "山田太郎",
                     "email": "yamada@example.com", "password": "password123"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accessToken").value("access-token"))
        .andExpect(jsonPath("$.user.username").value("yamada"))
        .andExpect(jsonPath("$.user.displayName").value("山田太郎"))
        .andExpect(jsonPath("$.user.iconUrl").isEmpty())
        .andExpect(jsonPath("$.user.bio").isEmpty())
        .andExpect(jsonPath("$.user.email").value("yamada@example.com"))
        .andExpect(jsonPath("$.user.emailVerified").value(false))
        .andExpect(jsonPath("$.user.isPrivate").value(false))
        .andExpect(jsonPath("$.user.weightUnit").value("kg"))
        .andExpect(jsonPath("$.user.distanceUnit").value("km"))
        // パスワードのハッシュは返さない
        .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
        // リフレッシュトークンは JSON には入れず、Cookie で渡す
        .andExpect(jsonPath("$.refreshToken").doesNotExist())
        .andExpect(header().string(HttpHeaders.SET_COOKIE, matchesPattern(REFRESH_COOKIE)));
  }

  /** 入力チェックの境界値（docs/test-plan.md 5.1）。有効な値の境界は、登録できる */
  @ParameterizedTest(name = "{0} = {1}")
  @MethodSource("validBoundaries")
  void 境界の内側の値なら登録できる(String field, String value) throws Exception {
    given(authService.signup(anyString(), anyString(), anyString(), anyString()))
        .willReturn(new AuthResult("access-token", "refresh-token", user()));

    mockMvc.perform(signup(validRequestWith(field, value))).andExpect(status().isCreated());
  }

  static Stream<Arguments> validBoundaries() {
    return Stream.of(
        arguments("username", "abcd"),
        arguments("username", "abcdefghij_1234"),
        arguments("displayName", "山"),
        arguments("displayName", "あ".repeat(50)),
        // 絵文字も1文字と数える（UTF-16 では2つ分）
        arguments("displayName", "💪".repeat(50)),
        // 前後の空白は数えない
        arguments("displayName", "  " + "あ".repeat(50) + "  "),
        arguments("password", "a".repeat(8)),
        arguments("password", "a".repeat(72)),
        // 日本語は1文字3バイト。24文字で72バイト
        arguments("password", "あ".repeat(24)));
  }

  /** 入力チェックの境界値（docs/test-plan.md 5.1）。無効な値は、項目ごとの文言で 400 を返す */
  @ParameterizedTest(name = "{0} = {1}")
  @MethodSource("invalidValues")
  void 境界の外側の値なら400と項目ごとのエラーを返す(String field, String value, String message) throws Exception {
    mockMvc
        .perform(signup(validRequestWith(field, value)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors.length()").value(1))
        .andExpect(jsonPath("$.errors[0].field").value(field))
        .andExpect(jsonPath("$.errors[0].message").value(message));
  }

  static Stream<Arguments> invalidValues() {
    String username = "ユーザー名は半角英数字と_で4〜15文字で入力してください";
    String displayName = "表示名は1〜50文字で入力してください";
    String email = "メールアドレスの形式が正しくありません";
    return Stream.of(
        arguments("username", "abc", username),
        arguments("username", "abcdefghij_12345", username),
        arguments("username", "ｙａｍａｄａ", username),
        arguments("username", "yama-da", username),
        arguments("username", null, username),
        arguments("displayName", "", displayName),
        arguments("displayName", "   ", displayName),
        arguments("displayName", "あ".repeat(51), displayName),
        arguments("displayName", null, displayName),
        arguments("email", "", email),
        arguments("email", "yamada", email),
        arguments("email", null, email),
        arguments("password", "a".repeat(7), "パスワードは8文字以上で入力してください"),
        arguments("password", "a".repeat(73), "パスワードは72文字以内で入力してください"),
        arguments("password", null, "パスワードは8文字以上で入力してください"),
        // 25文字（72文字以内）だが、75バイトで BCrypt が扱えない
        arguments("password", "あ".repeat(25), "パスワードが長すぎます（全角文字は1文字を3バイトとして、72バイトまで）"),
        // 絵文字37個は UTF-16 で74個分。文字数のエラーだけを出す（バイト数のエラーと2つ出さない）
        arguments("password", "💪".repeat(37), "パスワードは72文字以内で入力してください"));
  }

  @Test
  void JSONが壊れていれば400のMALFORMED_REQUESTを返す() throws Exception {
    mockMvc
        .perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
  }

  @Test
  void ログインできたら200とトークンとユーザーの情報を返す() throws Exception {
    given(authService.login("yamada@example.com", "password123"))
        .willReturn(new AuthResult("access-token", "refresh-token", user()));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"yamada@example.com\", \"password\": \"password123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("access-token"))
        .andExpect(jsonPath("$.user.username").value("yamada"))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, matchesPattern(REFRESH_COOKIE)));
  }

  @Test
  void 再発行は_Cookieのリフレッシュトークンで_新しいトークンとCookieを返す() throws Exception {
    given(authService.refresh("old-refresh-token"))
        .willReturn(new AuthResult("access-token", "refresh-token", user()));

    // アクセストークンなしで呼べる
    mockMvc
        .perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", "old-refresh-token")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("access-token"))
        .andExpect(jsonPath("$.user.username").value("yamada"))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, matchesPattern(REFRESH_COOKIE)));
  }

  @Test
  void 再発行でCookieがなければ401のSESSION_EXPIRED() throws Exception {
    mockMvc
        .perform(post("/api/auth/refresh"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    verifyNoInteractions(authService);
  }

  @Test
  void 再発行でリフレッシュトークンが使えなければ401のSESSION_EXPIRED() throws Exception {
    given(authService.refresh("old-refresh-token"))
        .willThrow(new ApiException(ErrorCode.SESSION_EXPIRED));

    mockMvc
        .perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", "old-refresh-token")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
  }

  @Test
  void ログアウトはリフレッシュトークンを無効にし_204とCookieを消す指示を返す() throws Exception {
    mockMvc
        .perform(post("/api/auth/logout").cookie(new Cookie("refresh_token", "refresh-token")))
        .andExpect(status().isNoContent())
        .andExpect(
            header()
                .string(
                    HttpHeaders.SET_COOKIE,
                    matchesPattern(
                        "refresh_token=; Path=/api/auth; Max-Age=0; Expires=.*; HttpOnly;"
                            + " SameSite=Strict")));
    verify(authService).logout("refresh-token");
  }

  @Test
  void ログアウトはCookieがなくても204() throws Exception {
    mockMvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
    verifyNoInteractions(authService);
  }

  @Test
  void ログインでメールアドレスかパスワードが違えば401のINVALID_CREDENTIALS() throws Exception {
    given(authService.login("yamada@example.com", "wrong"))
        .willThrow(new ApiException(ErrorCode.INVALID_CREDENTIALS));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"yamada@example.com\", \"password\": \"wrong\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.message").value("メールアドレスまたはパスワードが正しくありません"));
  }

  @Test
  void ログインでメールアドレスとパスワードが空なら400と項目ごとのエラー() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \" \", \"password\": \"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[?(@.field == 'email')].message").value("メールアドレスを入力してください"))
        .andExpect(jsonPath("$.errors[?(@.field == 'password')].message").value("パスワードを入力してください"));
  }

  @Test
  void ログイン中のユーザーを返す() throws Exception {
    given(authService.me(1L)).willReturn(user());

    mockMvc
        .perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("yamada"))
        .andExpect(jsonPath("$.email").value("yamada@example.com"));
  }

  @Test
  void ログイン中のユーザーはログインしていなければ401() throws Exception {
    mockMvc
        .perform(get("/api/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void 形の壊れたトークンは401() throws Exception {
    mockMvc
        .perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer broken"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void 存在しないURLは404のRESOURCE_NOT_FOUND() throws Exception {
    mockMvc
        .perform(get("/api/no-such-api").header(HttpHeaders.AUTHORIZATION, bearer(1L)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void 使えないメソッドは405のMETHOD_NOT_ALLOWED() throws Exception {
    mockMvc
        .perform(get("/api/auth/signup").header(HttpHeaders.AUTHORIZATION, bearer(1L)))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
  }

  /** 14日（1209600秒）・JavaScript から読めない・ほかのサイトからのリクエストには付かない・/api/auth の下にだけ送る */
  private static final String REFRESH_COOKIE =
      "refresh_token=refresh-token; Path=/api/auth; Max-Age=1209600; Expires=.*; HttpOnly;"
          + " SameSite=Strict";

  private String bearer(long userId) {
    return "Bearer " + jwtService.issue(userId);
  }

  /** 正しい新規登録のリクエストの、1つの項目だけを置き換えたもの（null なら、その項目を送らない） */
  private static Map<String, String> validRequestWith(String field, String value) {
    Map<String, String> body = new HashMap<>();
    body.put("username", "yamada");
    body.put("displayName", "山田太郎");
    body.put("email", "yamada@example.com");
    body.put("password", "password123");
    if (value == null) {
      body.remove(field);
    } else {
      body.put(field, value);
    }
    return body;
  }

  private MockHttpServletRequestBuilder signup(Map<String, String> body) {
    return post("/api/auth/signup")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body));
  }

  private static UsersRecord user() {
    OffsetDateTime now = OffsetDateTime.parse("2026-11-20T10:00:00+09:00");
    UsersRecord user = new UsersRecord();
    user.setId(1L);
    user.setUsername("yamada");
    user.setDisplayName("山田太郎");
    user.setEmail("yamada@example.com");
    user.setPasswordHash("hash");
    user.setIsPrivate(false);
    user.setWeightUnit("kg");
    user.setDistanceUnit("km");
    user.setCreatedAt(now);
    user.setUpdatedAt(now);
    return user;
  }
}
