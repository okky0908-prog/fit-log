package com.okimoto.fitlog.auth.config;

import com.okimoto.fitlog.auth.service.JwtService;
import com.okimoto.fitlog.common.controller.ApiError;
import com.okimoto.fitlog.common.controller.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * 認証の設定。
 *
 * <ul>
 *   <li>セッションは使わず、リクエストごとに JWT で本人を確かめる
 *   <li>ログインなしで呼べるのは、新規登録・ログイン・再発行・ログアウトだけ。ほかの API はログインが必要（docs/api.md の一覧の「認証」の列）
 *   <li>ログインしていなければ 401、権限がなければ 403 を、docs/api.md のエラーの形（JSON）で返す
 * </ul>
 */
@Configuration
@EnableConfigurationProperties({
  JwtProperties.class,
  RefreshTokenProperties.class,
  LoginAttemptProperties.class,
  PasswordHashingProperties.class
})
public class SecurityConfig {

  /** ログインなしで呼べる API（すべて POST）。再発行・ログアウトは、アクセストークンの代わりにリフレッシュトークンの Cookie を使う */
  static final String[] PUBLIC_POST_PATHS = {
    "/api/auth/signup", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout"
  };

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtService jwtService, ObjectMapper objectMapper) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth ->
            auth.requestMatchers(HttpMethod.POST, PUBLIC_POST_PATHS)
                .permitAll()
                // エラーの転送先（ApiErrorController）。ここを閉じると、ログインなしの 404 などが 401 になってしまう
                .requestMatchers("/error")
                .permitAll()
                .anyRequest()
                .authenticated())
        .exceptionHandling(handling ->
            handling
                .authenticationEntryPoint((request, response, e) ->
                    writeError(response, objectMapper, ErrorCode.UNAUTHENTICATED))
                .accessDeniedHandler((request, response, e) ->
                    writeError(response, objectMapper, ErrorCode.FORBIDDEN)))
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  private static void writeError(
      HttpServletResponse response, ObjectMapper objectMapper, ErrorCode code) throws IOException {
    response.setStatus(code.status().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(objectMapper.writeValueAsString(ApiError.of(code)));
  }
}
