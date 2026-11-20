package com.okimoto.fitlog.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 推測できないランダムなトークン（リフレッシュトークン・確認用のリンクのトークン）を作り、DB に保存するためのハッシュにする。
 *
 * <p>DB にはハッシュだけを保存する。DB が漏れても、トークンとして使えないように
 */
final class OpaqueTokens {

  private static final int TOKEN_BYTES = 32;
  private static final SecureRandom RANDOM = new SecureRandom();

  private OpaqueTokens() {}

  /** 256ビットのランダムな値を、URL にそのまま入れられる Base64URL（43文字）にしたもの */
  static String generate() {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** トークンの SHA-256 のハッシュ（16進数64文字） */
  static String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 が使えません", e);
    }
  }
}
