package com.okimoto.fitlog.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.okimoto.fitlog.auth.config.RefreshTokenProperties;
import com.okimoto.fitlog.auth.repository.RefreshTokenRepository;
import com.okimoto.fitlog.jooq.tables.records.RefreshTokensRecord;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * リフレッシュトークンの状態遷移（docs/test-plan.md 5.x「リフレッシュトークン」。前回の遷移表と同じ）。
 *
 * <pre>
 * 有効 ─再発行→ 交換済み ─10秒以内にもう一度→ 新しいトークン（同時のリクエスト）
 *                        ─10秒より後にもう一度→ そのユーザーのトークンをすべて無効（使い回しの検知）
 * 有効 ─ログアウト→ ログアウト済み ─再発行→ すべて無効
 * 有効 ─期限が切れる→ 期限切れ ─再発行→ 401（全無効化しない）
 * </pre>
 */
class RefreshTokenServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-20T01:00:00Z");
  private static final OffsetDateTime NOW_ODT = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
  private static final long USER_ID = 7L;
  private static final long TOKEN_ID = 100L;

  private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
  private final RefreshTokenService service =
      new RefreshTokenService(
          repository,
          new RefreshTokenProperties(
              Duration.ofDays(14), false, Duration.ofSeconds(10), "0 0 4 * * *"),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void 発行したトークンはハッシュだけを保存し_期限は14日後() {
    String token = service.issue(USER_ID);

    ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
    verify(repository).insert(eq(USER_ID), hash.capture(), eq(NOW_ODT.plusDays(14)), eq(NOW_ODT));
    assertThat(hash.getValue()).isEqualTo(RefreshTokenService.hash(token)).hasSize(64);
    assertThat(token).isNotEqualTo(hash.getValue());
    // 推測できない長さ（32バイトを Base64URL にした43文字）
    assertThat(token).hasSize(43);
  }

  @Test
  void 発行するたびに違うトークンになる() {
    assertThat(service.issue(USER_ID)).isNotEqualTo(service.issue(USER_ID));
  }

  @Test
  void ハッシュはSHA256の16進数() {
    // echo -n abc | shasum -a 256
    assertThat(RefreshTokenService.hash("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }

  @Test
  void 有効なトークンは交換して新しいトークンを返す() {
    given(repository.findByTokenHash(RefreshTokenService.hash("t"))).willReturn(found(valid()));
    given(repository.rotate(TOKEN_ID, NOW_ODT)).willReturn(true);

    Optional<RefreshTokenService.Rotation> rotation = service.rotate("t");

    assertThat(rotation).hasValueSatisfying(r -> assertThat(r.userId()).isEqualTo(USER_ID));
    assertThat(rotation.get().refreshToken()).isNotEqualTo("t");
    verify(repository).insert(eq(USER_ID), any(), any(), any());
  }

  @Test
  void 交換して10秒ちょうどまでの再利用は_同時のリクエストとして新しいトークンを返す() {
    given(repository.findByTokenHash(any())).willReturn(found(rotatedAt(NOW_ODT.minusSeconds(10))));

    assertThat(service.rotate("t")).isPresent();
    verify(repository, never()).revokeAllByUserId(anyLong(), any());
  }

  @Test
  void 交換して10秒より後の再利用は_そのユーザーのトークンをすべて無効にする() {
    given(repository.findByTokenHash(any())).willReturn(found(rotatedAt(NOW_ODT.minusSeconds(11))));

    assertThat(service.rotate("t")).isEmpty();
    verify(repository).revokeAllByUserId(USER_ID, NOW_ODT);
  }

  @Test
  void 交換して10秒以内でも_期限が切れていれば同時のリクエストとして許さない() {
    RefreshTokensRecord token = rotatedAt(NOW_ODT.minusSeconds(1));
    token.setExpiresAt(NOW_ODT);
    given(repository.findByTokenHash(any())).willReturn(found(token));

    assertThat(service.rotate("t")).isEmpty();
    verify(repository).revokeAllByUserId(USER_ID, NOW_ODT);
  }

  @Test
  void ログアウト済みのトークンが使われたら_そのユーザーのトークンをすべて無効にする() {
    RefreshTokensRecord token = valid();
    token.setRevokedAt(NOW_ODT.minusSeconds(1));
    given(repository.findByTokenHash(any())).willReturn(found(token));

    assertThat(service.rotate("t")).isEmpty();
    verify(repository).revokeAllByUserId(USER_ID, NOW_ODT);
  }

  @Test
  void 期限ちょうどのトークンは使えない_全無効化はしない() {
    RefreshTokensRecord token = valid();
    token.setExpiresAt(NOW_ODT);
    given(repository.findByTokenHash(any())).willReturn(found(token));

    assertThat(service.rotate("t")).isEmpty();
    verify(repository, never()).rotate(anyLong(), any());
    verify(repository, never()).revokeAllByUserId(anyLong(), any());
  }

  @Test
  void 期限の1秒前のトークンは使える() {
    RefreshTokensRecord token = valid();
    token.setExpiresAt(NOW_ODT.plusSeconds(1));
    given(repository.findByTokenHash(any())).willReturn(found(token));
    given(repository.rotate(TOKEN_ID, NOW_ODT)).willReturn(true);

    assertThat(service.rotate("t")).isPresent();
  }

  @Test
  void 知らないトークンは使えない_全無効化はしない() {
    given(repository.findByTokenHash(any())).willReturn(Optional.empty());

    assertThat(service.rotate("t")).isEmpty();
    verify(repository, never()).revokeAllByUserId(anyLong(), any());
  }

  /** 読んでから交換するまでの間に、ほかのリクエストが先に交換した（2つのタブがほぼ同時に再発行した） */
  @Test
  void ほかのリクエストが先に交換していたら_同時のリクエストとして新しいトークンを返す() {
    given(repository.findByTokenHash(any())).willReturn(found(valid()), found(rotatedAt(NOW_ODT)));
    given(repository.rotate(TOKEN_ID, NOW_ODT)).willReturn(false);

    assertThat(service.rotate("t")).isPresent();
    verify(repository, never()).revokeAllByUserId(anyLong(), any());
  }

  @Test
  void ログアウトでトークンを無効にする() {
    given(repository.findByTokenHash(RefreshTokenService.hash("t"))).willReturn(found(valid()));

    service.revoke("t");

    verify(repository).revoke(TOKEN_ID, NOW_ODT);
  }

  @Test
  void 知らないトークンのログアウトは何もしない() {
    given(repository.findByTokenHash(any())).willReturn(Optional.empty());

    service.revoke("t");

    verify(repository, never()).revoke(anyLong(), any());
  }

  @Test
  void 期限切れの行を今の時刻で消す() {
    given(repository.deleteExpired(NOW_ODT)).willReturn(3);

    assertThat(service.deleteExpired()).isEqualTo(3);
  }

  private static RefreshTokensRecord valid() {
    RefreshTokensRecord token = new RefreshTokensRecord();
    token.setId(TOKEN_ID);
    token.setUserId(USER_ID);
    token.setTokenHash("x".repeat(64));
    token.setExpiresAt(NOW_ODT.plusDays(13));
    token.setCreatedAt(NOW_ODT.minusDays(1));
    return token;
  }

  private static RefreshTokensRecord rotatedAt(OffsetDateTime rotatedAt) {
    RefreshTokensRecord token = valid();
    token.setRevokedAt(rotatedAt);
    token.setRotatedAt(rotatedAt);
    return token;
  }

  private static Optional<RefreshTokensRecord> found(RefreshTokensRecord token) {
    return Optional.of(token);
  }
}
