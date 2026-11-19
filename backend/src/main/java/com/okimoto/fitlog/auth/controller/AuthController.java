package com.okimoto.fitlog.auth.controller;

import com.okimoto.fitlog.auth.config.AuthenticatedUser;
import com.okimoto.fitlog.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 認証の API（docs/api.md の A-01〜A-03） */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  /** A-01 新規登録。登録のあとはそのままログイン状態にするので、トークンも返す */
  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthResponse signup(@Valid @RequestBody SignupRequest request) {
    return AuthResponse.from(
        authService.signup(
            request.username(), request.displayName(), request.email(), request.password()));
  }

  /** A-02 ログイン */
  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody LoginRequest request) {
    return AuthResponse.from(authService.login(request.email(), request.password()));
  }

  /** A-03 ログイン中のユーザー */
  @GetMapping("/me")
  public MeResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
    return MeResponse.from(authService.me(user.id()));
  }
}
