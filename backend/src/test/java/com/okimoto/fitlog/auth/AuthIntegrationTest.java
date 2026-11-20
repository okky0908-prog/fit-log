package com.okimoto.fitlog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.okimoto.fitlog.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 新規登録・ログイン・ログイン中のユーザーを、API から DB まで通して確かめる。
 *
 * <p>DB はテスト全体で共有するので、テストごとに違うユーザー名・メールアドレスを使う（ほかのテストのデータに頼らない）
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

  private static final AtomicInteger SEQUENCE = new AtomicInteger();

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void 新規登録で返ったトークンで_ログイン中のユーザーを取れる() throws Exception {
    String username = uniqueUsername();

    String token =
        accessToken(
            signup(username, "Mixed." + username + "@Example.com")
                .andReturn()
                .getResponse()
                .getContentAsString());

    mockMvc
        .perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value(username))
        // 小文字にそろえて保存されている
        .andExpect(jsonPath("$.email").value("mixed." + username.toLowerCase() + "@example.com"))
        .andExpect(jsonPath("$.emailVerified").value(false));
  }

  @Test
  void 新規登録したメールアドレスとパスワードでログインできる() throws Exception {
    String username = uniqueUsername();
    String email = username + "@example.com";
    signup(username, email).andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new Login(email, "password123"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.username").value(username));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new Login(email, "wrong-password"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void 大文字だけ違う既存のユーザー名では登録できない() throws Exception {
    String username = uniqueUsername();
    signup(username, username + "@example.com").andExpect(status().isCreated());

    signup(username.toUpperCase(), "other." + username + "@example.com")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ALREADY_REGISTERED"))
        .andExpect(jsonPath("$.errors[0].field").value("username"));
  }

  /** docs/test-plan.md 4.6。2つのスレッドから同時に呼ぶ */
  @Test
  void 同じユーザー名で同時に新規登録すると_1件だけ201でもう1件は409() throws Exception {
    String username = uniqueUsername();
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      List<Future<Integer>> results =
          List.of(1, 2).stream()
              .map(i ->
                  executor.submit(() -> {
                    start.await();
                    return signup(username, "user" + i + "." + username + "@example.com")
                        .andReturn()
                        .getResponse()
                        .getStatus();
                  }))
              .toList();
      start.countDown();

      assertThat(List.of(results.get(0).get(), results.get(1).get()))
          .containsExactlyInAnyOrder(201, 409);
    }
  }

  /** リフレッシュトークンの状態遷移を、API から DB まで通して確かめる（交換・猶予の時間の中の再利用・ログアウト後の使い回し） */
  @Test
  void 再発行でトークンを交換し_ログアウトしたトークンが使われたら_そのユーザーのトークンをすべて無効にする() throws Exception {
    String username = uniqueUsername();
    Cookie first = refreshCookie(signup(username, username + "@example.com").andReturn());

    // 交換すると、新しいアクセストークンで Me を取れる
    MvcResult refreshed = refresh(first).andExpect(status().isOk()).andReturn();
    Cookie second = refreshCookie(refreshed);
    assertThat(second.getValue()).isNotEqualTo(first.getValue());
    mockMvc
        .perform(
            get("/api/auth/me")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + accessToken(refreshed.getResponse().getContentAsString())))
        .andExpect(status().isOk());

    // 交換したばかり（10秒以内）の古いトークンは、同時のリクエストとして使える
    Cookie third = refreshCookie(refresh(first).andExpect(status().isOk()).andReturn());

    // ログアウトしたトークンをもう一度使うと 401。盗まれた可能性があるので、ほかのトークン（third）も使えなくなる
    mockMvc.perform(post("/api/auth/logout").cookie(second)).andExpect(status().isNoContent());
    refresh(second)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    refresh(third).andExpect(status().isUnauthorized());
  }

  @Test
  void 知らないリフレッシュトークンは401() throws Exception {
    refresh(new Cookie("refresh_token", "unknown-token"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
  }

  /** docs/test-plan.md 4.8。5回失敗したら、正しいパスワードでも 429 */
  @Test
  void ログインに5回失敗したら_正しいパスワードでも429() throws Exception {
    String username = uniqueUsername();
    String email = username + "@example.com";
    signup(username, email).andExpect(status().isCreated());
    for (int i = 0; i < 5; i++) {
      login(email, "wrong-password").andExpect(status().isUnauthorized());
    }

    login(email, "password123")
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"));
  }

  private org.springframework.test.web.servlet.ResultActions refresh(Cookie cookie)
      throws Exception {
    return mockMvc.perform(post("/api/auth/refresh").cookie(cookie));
  }

  private org.springframework.test.web.servlet.ResultActions login(String email, String password)
      throws Exception {
    return mockMvc.perform(
        post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(new Login(email, password))));
  }

  private static Cookie refreshCookie(MvcResult result) {
    Cookie cookie = result.getResponse().getCookie("refresh_token");
    assertThat(cookie).isNotNull();
    return cookie;
  }

  private org.springframework.test.web.servlet.ResultActions signup(String username, String email)
      throws Exception {
    return mockMvc.perform(
        post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(new Signup(username, "表示名", email, "password123"))));
  }

  private String accessToken(String body) {
    JsonNode node = objectMapper.readTree(body);
    return node.get("accessToken").asString();
  }

  private String json(Object body) {
    return objectMapper.writeValueAsString(body);
  }

  /** ユーザー名は15文字まで。テストごとに違う名前にする */
  private static String uniqueUsername() {
    return "it_user_" + SEQUENCE.incrementAndGet() + "_" + (System.nanoTime() % 10_000);
  }

  private record Signup(String username, String displayName, String email, String password) {}

  private record Login(String email, String password) {}
}
