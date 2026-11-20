package com.okimoto.fitlog.auth.controller;

/**
 * A-06 のリクエスト。
 *
 * @param token 確認用のリンクの {@code ?token=} の値。ない・空のときは、不正なトークンとして 400（INVALID_TOKEN）にする
 */
public record VerifyEmailRequest(String token) {}
