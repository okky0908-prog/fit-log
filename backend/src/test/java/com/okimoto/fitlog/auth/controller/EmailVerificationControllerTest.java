package com.okimoto.fitlog.auth.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.okimoto.fitlog.auth.config.SecurityConfig;
import com.okimoto.fitlog.auth.service.EmailVerificationService;
import com.okimoto.fitlog.auth.service.JwtService;
import com.okimoto.fitlog.common.config.ClockConfig;
import com.okimoto.fitlog.common.controller.ApiException;
import com.okimoto.fitlog.common.controller.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** メールアドレスの確認（A-06）・確認メールの再送（A-07）の入口。Service は偽物、認証の設定は本物 */
@WebMvcTest(EmailVerificationController.class)
@Import({SecurityConfig.class, JwtService.class, ClockConfig.class})
@ActiveProfiles("test")
class EmailVerificationControllerTest {

  private static final long USER_ID = 7L;

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @MockitoBean private EmailVerificationService emailVerificationService;

  // ===== A-06 =====

  /** 別の端末でメールを開くことがあるので、ログインしていなくても呼べる */
  @Test
  void ログインしていなくても確認でき_結果とアドレスを返す() throws Exception {
    given(emailVerificationService.verify("abc")).willReturn("yamada@example.com");

    mockMvc
        .perform(
            post("/api/auth/email-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"abc\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result").value("verified"))
        .andExpect(jsonPath("$.email").value("yamada@example.com"));
  }

  @Test
  void 使えないトークンなら400のINVALID_TOKEN() throws Exception {
    given(emailVerificationService.verify("abc"))
        .willThrow(new ApiException(ErrorCode.INVALID_TOKEN));

    mockMvc
        .perform(
            post("/api/auth/email-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"abc\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
  }

  /** トークンのない本文は、不正なトークンと同じに扱う（Service が 400 INVALID_TOKEN にする） */
  @Test
  void トークンがなければ_そのままServiceに渡す() throws Exception {
    given(emailVerificationService.verify(null))
        .willThrow(new ApiException(ErrorCode.INVALID_TOKEN));

    mockMvc
        .perform(
            post("/api/auth/email-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
  }

  // ===== A-07 =====

  @Test
  void 再送はログイン中のユーザーに送って202() throws Exception {
    mockMvc
        .perform(
            post("/api/account/email-verification")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(USER_ID)))
        .andExpect(status().isAccepted());

    verify(emailVerificationService).resend(USER_ID);
  }

  @Test
  void 再送はログインしていなければ401() throws Exception {
    mockMvc
        .perform(post("/api/account/email-verification"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

    verifyNoInteractions(emailVerificationService);
  }

  @ParameterizedTest
  @CsvSource({"ALREADY_VERIFIED, 409", "TOO_MANY_EMAILS, 429"})
  void 再送できないときはServiceのエラーを返す(ErrorCode code, int status) throws Exception {
    willThrow(new ApiException(code)).given(emailVerificationService).resend(USER_ID);

    mockMvc
        .perform(
            post("/api/account/email-verification")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(USER_ID)))
        .andExpect(status().is(status))
        .andExpect(jsonPath("$.code").value(code.name()));
  }
}
