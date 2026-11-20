package com.okimoto.fitlog.auth;

import static com.okimoto.fitlog.jooq.Tables.EMAIL_VERIFICATION_TOKENS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.okimoto.fitlog.mail.repository.MailSendLogRepository;
import com.okimoto.fitlog.mail.repository.MailType;
import com.okimoto.fitlog.support.MailpitClient;
import com.okimoto.fitlog.support.MailpitClient.Mail;
import com.okimoto.fitlog.support.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/**
 * 確認メール（A-01 の送信・A-06・A-07）を、API から DB・Mailpit まで通して確かめる。
 *
 * <p>メールは保存の確定後に別のスレッドで送るので、届くのを Awaitility で待つ（Thread.sleep は使わない）。DB と Mailpit
 * はテスト全体で共有するので、テストごとに違うアドレスを使う
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailVerificationIntegrationTest {

  private static final AtomicInteger SEQUENCE = new AtomicInteger();
  private static final Pattern LINK =
      Pattern.compile("http://localhost:5173/verify-email\\?token=(\\S+)");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private MailSendLogRepository mailSendLogRepository;
  @Autowired private Clock clock;
  @Autowired private DSLContext dsl;

  @Test
  void 新規登録すると確認メールが届き_リンクのトークンで確認済みになる_2回目は使えない() throws Exception {
    User user = signup();

    Mail mail = awaitMails(user.email(), 1).getFirst();
    assertThat(mail.subject()).isEqualTo("【FIT LOG】メールアドレスの確認");

    verify(tokenIn(mail))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result").value("verified"))
        .andExpect(jsonPath("$.email").value(user.email()));
    me(user).andExpect(jsonPath("$.emailVerified").value(true));

    verify(tokenIn(mail))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
  }

  @Test
  void 再送すると新しいリンクが届き_前のリンクは使えない_確認済みなら409() throws Exception {
    User user = signup();
    Mail first = awaitMails(user.email(), 1).getFirst();

    resend(user).andExpect(status().isAccepted());
    Mail second = awaitMails(user.email(), 2).getFirst();

    verify(tokenIn(first)).andExpect(status().isBadRequest());
    verify(tokenIn(second)).andExpect(status().isOk());
    resend(user)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ALREADY_VERIFIED"));
  }

  @Test
  void 同じアドレスに1時間で5通まで_6通目は429で届かない() throws Exception {
    User user = signup();
    awaitMails(user.email(), 1);

    for (int i = 0; i < 4; i++) {
      resend(user).andExpect(status().isAccepted());
    }
    resend(user)
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("TOO_MANY_EMAILS"));

    awaitMails(user.email(), 5);
    // 6通目が届かないこと（別のスレッドではなく応答の前に送るので、待たずに数えてよい）
    assertThat(MailpitClient.messagesTo(user.email())).hasSize(5);
  }

  /** 4通送ったアドレスに同時に2回送ると、送るのは1通だけ（docs/test-plan.md 4.6）。送り先のアドレスのロックを外すと、両方が「まだ4通」と数えて落ちる */
  @Test
  void 四通送ったアドレスに同時に2回再送すると_1回だけ送り_もう一方は429() throws Exception {
    User user = signup();
    awaitMails(user.email(), 1);
    for (int i = 0; i < 3; i++) {
      mailSendLogRepository.insert(user.email(), MailType.VERIFY_EMAIL, OffsetDateTime.now(clock));
    }

    CountDownLatch start = new CountDownLatch(1);
    List<Integer> statuses;
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      List<Future<Integer>> results =
          List.of(
              executor.submit(() -> resendAfter(start, user)),
              executor.submit(() -> resendAfter(start, user)));
      start.countDown();
      statuses = List.of(results.get(0).get(), results.get(1).get());
    }

    assertThat(statuses).containsExactlyInAnyOrder(202, 429);
    assertThat(
            mailSendLogRepository.countSentAfter(
                user.email(), OffsetDateTime.now(clock).minusHours(1)))
        .isEqualTo(5);
  }

  @Test
  void 確認用のトークンはDBにハッシュだけが入る() throws Exception {
    User user = signup();
    String token = tokenIn(awaitMails(user.email(), 1).getFirst());

    String saved =
        dsl.select(EMAIL_VERIFICATION_TOKENS.TOKEN_HASH)
            .from(EMAIL_VERIFICATION_TOKENS)
            .where(EMAIL_VERIFICATION_TOKENS.EMAIL.eq(user.email()))
            .fetchSingle(EMAIL_VERIFICATION_TOKENS.TOKEN_HASH);
    // echo -n <token> | shasum -a 256 と同じ値。元の値は入らない
    assertThat(saved)
        .isEqualTo(
            HexFormat.of()
                .formatHex(
                    MessageDigest.getInstance("SHA-256")
                        .digest(token.getBytes(StandardCharsets.UTF_8))))
        .isNotEqualTo(token);
  }

  private int resendAfter(CountDownLatch start, User user) throws Exception {
    start.await();
    return resend(user).andReturn().getResponse().getStatus();
  }

  private User signup() throws Exception {
    String username = "ev_" + SEQUENCE.incrementAndGet() + "_" + (System.nanoTime() % 10_000);
    String email = username + "@example.com";
    String body =
        mockMvc
            .perform(
                post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new Signup(username, "表示名", email, "password123"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return new User(email, objectMapper.readTree(body).get("accessToken").asString());
  }

  private ResultActions me(User user) throws Exception {
    return mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, user.bearer()));
  }

  private ResultActions resend(User user) throws Exception {
    return mockMvc.perform(
        post("/api/account/email-verification").header(HttpHeaders.AUTHORIZATION, user.bearer()));
  }

  private ResultActions verify(String token) throws Exception {
    return mockMvc.perform(
        post("/api/auth/email-verification")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new VerifyEmail(token))));
  }

  /** 宛先にメールが count 通届くまで待ち、新しい順に返す */
  private static List<Mail> awaitMails(String email, int count) {
    return await()
        .atMost(Duration.ofSeconds(5))
        .until(() -> MailpitClient.messagesTo(email), mails -> mails.size() >= count);
  }

  private static String tokenIn(Mail mail) {
    Matcher matcher = LINK.matcher(mail.text());
    assertThat(matcher.find()).as("本文にリンクがある：%s", mail.text()).isTrue();
    return matcher.group(1);
  }

  private record User(String email, String accessToken) {
    String bearer() {
      return "Bearer " + accessToken;
    }
  }

  private record Signup(String username, String displayName, String email, String password) {}

  private record VerifyEmail(String token) {}
}
