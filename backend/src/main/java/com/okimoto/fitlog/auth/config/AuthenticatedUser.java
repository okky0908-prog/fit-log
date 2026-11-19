package com.okimoto.fitlog.auth.config;

/**
 * ログイン中のユーザー。{@link JwtAuthenticationFilter} がトークンから作り、Controller では
 * {@code @AuthenticationPrincipal AuthenticatedUser user} で受け取る
 *
 * @param id ユーザーの ID
 */
public record AuthenticatedUser(long id) {}
