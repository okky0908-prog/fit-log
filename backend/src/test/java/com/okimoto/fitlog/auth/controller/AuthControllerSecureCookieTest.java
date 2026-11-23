package com.okimoto.fitlog.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.okimoto.fitlog.auth.config.SecurityConfig;
import com.okimoto.fitlog.auth.service.AuthResult;
import com.okimoto.fitlog.auth.service.AuthService;
import com.okimoto.fitlog.auth.service.JwtService;
import com.okimoto.fitlog.common.config.ClockConfig;
import com.okimoto.fitlog.image.service.ImageUrls;
import com.okimoto.fitlog.jooq.tables.records.UsersRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 本番（HTTPS）の設定では、リフレッシュトークンの Cookie に Secure 属性を付ける */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtService.class, ClockConfig.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "app.refresh-token.cookie-secure=true")
class AuthControllerSecureCookieTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private AuthService authService;
  @MockitoBean private ImageUrls imageUrls;

  @Test
  void Secureの設定ならCookieにSecure属性を付ける() throws Exception {
    UsersRecord user = new UsersRecord();
    user.setUsername("yamada");
    user.setIsPrivate(false);
    given(authService.login("yamada@example.com", "password123"))
        .willReturn(new AuthResult("access-token", "refresh-token", user));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"yamada@example.com\", \"password\": \"password123\"}"))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("; Secure;")));
  }
}
