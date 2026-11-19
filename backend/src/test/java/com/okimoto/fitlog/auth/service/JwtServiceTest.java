package com.okimoto.fitlog.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.okimoto.fitlog.auth.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** アクセストークン（JWT）の発行と確認 */
class JwtServiceTest {

  private static final String SECRET = "test-secret-for-jwt-signing-0123456789abcdef";
  private static final Instant ISSUED_AT = Instant.parse("2026-11-20T01:00:00Z");

  private final MutableClock clock = new MutableClock(ISSUED_AT);
  private final JwtService jwtService = jwtService(SECRET);

  @Test
  void 発行したトークンからユーザーのIDを取り出せる() {
    assertThat(jwtService.parseUserId(jwtService.issue(42L))).contains(42L);
  }

  @Test
  void 有効期限の15分の1秒前までは使える() {
    String token = jwtService.issue(42L);

    clock.set(ISSUED_AT.plus(Duration.ofMinutes(15)).minusSeconds(1));

    assertThat(jwtService.parseUserId(token)).contains(42L);
  }

  @Test
  void 有効期限の15分を過ぎたら使えない() {
    String token = jwtService.issue(42L);

    clock.set(ISSUED_AT.plus(Duration.ofMinutes(15)).plusSeconds(1));

    assertThat(jwtService.parseUserId(token)).isEmpty();
  }

  @Test
  void 中身を書き換えたトークンは使えない() {
    String[] parts = jwtService.issue(42L).split("\\.");
    // 本文（subject）だけを別のユーザーのものに差し替える。署名が合わなくなる
    String otherBody = jwtService.issue(1L).split("\\.")[1];

    assertThat(jwtService.parseUserId(parts[0] + "." + otherBody + "." + parts[2])).isEmpty();
  }

  @Test
  void 別の鍵で署名したトークンは使えない() {
    String token = jwtService("another-secret-for-jwt-signing-0123456789ab").issue(42L);

    assertThat(jwtService.parseUserId(token)).isEmpty();
  }

  @Test
  void 署名のないトークンは使えない() {
    String token =
        Jwts.builder().subject("42").expiration(Date.from(ISSUED_AT.plusSeconds(60))).compact();

    assertThat(jwtService.parseUserId(token)).isEmpty();
  }

  @Test
  void ユーザーのIDが数字でないトークンは使えない() {
    String token =
        Jwts.builder()
            .subject("yamada")
            .expiration(Date.from(ISSUED_AT.plusSeconds(60)))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();

    assertThat(jwtService.parseUserId(token)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "broken", "a.b.c"})
  void 形の壊れたトークンは使えない(String token) {
    assertThat(jwtService.parseUserId(token)).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void 署名の鍵がなければ起動しない(String secret) {
    assertThatThrownBy(() -> jwtService(secret))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET");
  }

  @Test
  void 署名の鍵が32バイト未満なら起動しない() {
    assertThatThrownBy(() -> jwtService("a".repeat(31)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("31バイト");
  }

  @Test
  void 署名の鍵が32バイトなら起動する() {
    assertThat(jwtService("a".repeat(32)).issue(1L)).isNotBlank();
  }

  private JwtService jwtService(String secret) {
    return new JwtService(new JwtProperties(secret, Duration.ofMinutes(15)), clock);
  }

  /** 進められる時計 */
  private static final class MutableClock extends Clock {

    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void set(Instant now) {
      this.now = now;
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }
}
