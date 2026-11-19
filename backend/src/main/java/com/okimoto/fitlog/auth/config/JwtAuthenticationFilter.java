package com.okimoto.fitlog.auth.config;

import com.okimoto.fitlog.auth.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code Authorization: Bearer <token>} を確かめ、正しければログイン中のユーザーとして扱う。
 *
 * <p>トークンがない・正しくないときは何もしない。ログインが必要な API なら、そのあと {@link SecurityConfig} の設定で 401 になる。 Spring Boot
 * がサーブレットのフィルターとして二重に登録しないよう、Bean にはせず SecurityConfig の中で作る
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtService jwtService;

  public JwtAuthenticationFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      jwtService
          .parseUserId(header.substring(BEARER_PREFIX.length()))
          .ifPresent(userId ->
              SecurityContextHolder.getContext()
                  .setAuthentication(
                      UsernamePasswordAuthenticationToken.authenticated(
                          new AuthenticatedUser(userId), null, List.of())));
    }
    filterChain.doFilter(request, response);
  }
}
