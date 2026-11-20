package com.okimoto.fitlog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.okimoto.fitlog.mail.service.MailGateway;
import com.okimoto.fitlog.support.TestcontainersConfiguration;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** メールの送信が遅い・失敗するときの新規登録（docs/api.md A-01「メールが送れなくても登録は成功にする」）。メールの送信だけを偽物にし、ほかは本物（DB） */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SignupMailDelayIntegrationTest {

  private static final AtomicInteger SEQUENCE = new AtomicInteger();

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockitoBean private MailGateway mailGateway;

  /** 別のスレッドで送るので、登録の応答はメールの送信を待たない（送信が5秒止まっても、すぐに返る） */
  @Test
  void メールの送信が止まっていても_登録はすぐに201を返し_あとでメールを送る() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    willAnswer(
            invocation -> {
              release.await(5, TimeUnit.SECONDS);
              return null;
            })
        .given(mailGateway)
        .send(anyString(), anyString(), anyString());
    String email = uniqueEmail();

    long started = System.nanoTime();
    signup(email).andExpect(status().isCreated());
    Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
    release.countDown();

    assertThat(elapsed).isLessThan(Duration.ofSeconds(3));
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> verify(mailGateway).send(eq(email), anyString(), anyString()));
  }

  /** 登録は成功のまま。再送（A-07）では、送れなかったことを 503 で伝える */
  @Test
  void メールを送れなくても登録は成功し_再送は503() throws Exception {
    willThrow(new MailSendException("つながらない"))
        .given(mailGateway)
        .send(anyString(), anyString(), anyString());
    String email = uniqueEmail();

    String body =
        signup(email)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> verify(mailGateway).send(eq(email), anyString(), anyString()));

    mockMvc
        .perform(
            post("/api/account/email-verification")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + objectMapper.readTree(body).get("accessToken").asString()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
  }

  private org.springframework.test.web.servlet.ResultActions signup(String email) throws Exception {
    String username = email.substring(0, email.indexOf('@'));
    return mockMvc.perform(
        post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(
                    new Signup(username, "表示名", email, "password123"))));
  }

  private static String uniqueEmail() {
    return "md_" + SEQUENCE.incrementAndGet() + "_" + (System.nanoTime() % 10_000) + "@example.com";
  }

  private record Signup(String username, String displayName, String email, String password) {}
}
