package com.okimoto.fitlog.auth.service;

import com.okimoto.fitlog.auth.config.JwtProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * アクセストークン（JWT）の発行と検証。
 *
 * <p>トークンに入れるのは、ユーザーの ID（subject）と発行日時・有効期限だけ。メールアドレスなどの個人情報は入れない
 */
@Service
public class JwtService {

  /** 署名の鍵の最低の長さ（HS256 に必要な 256 ビット） */
  static final int MIN_SECRET_BYTES = 32;

  private final SecretKey key;
  private final JwtProperties properties;
  private final Clock clock;
  private final JwtParser parser;

  public JwtService(JwtProperties properties, Clock clock) {
    this.key = toKey(properties.secret());
    this.properties = properties;
    this.clock = clock;
    this.parser = Jwts.parser().verifyWith(key).clock(() -> Date.from(clock.instant())).build();
  }

  public String issue(long userId) {
    Instant now = clock.instant();
    return Jwts.builder()
        .subject(Long.toString(userId))
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(properties.expiration())))
        .signWith(key)
        .compact();
  }

  /** 署名と有効期限が正しければ、ユーザーの ID を返す。改ざん・期限切れ・形の誤りは空を返す */
  public Optional<Long> parseUserId(String token) {
    try {
      return Optional.of(Long.parseLong(parser.parseSignedClaims(token).getPayload().getSubject()));
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private static SecretKey toKey(String secret) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException(
          "JWT の署名の鍵がありません。.env か環境変数の JWT_SECRET に、32バイト以上のランダムな文字列を入れてください（例：openssl rand"
              + " -base64 48）");
    }
    byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < MIN_SECRET_BYTES) {
      throw new IllegalStateException(
          "JWT の署名の鍵が短すぎます（" + bytes.length + "バイト）。" + MIN_SECRET_BYTES + "バイト以上にしてください");
    }
    return Keys.hmacShaKeyFor(bytes);
  }
}
