package com.okimoto.fitlog.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.okimoto.fitlog.auth.config.LoginAttemptProperties;
import com.okimoto.fitlog.common.controller.ApiException;
import com.okimoto.fitlog.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** ログインの試行回数の制限（docs/test-plan.md 4.8。15分の間に5回失敗したら、15分ロックする） */
class LoginAttemptLimiterTest {

  private static final String EMAIL = "yamada@example.com";
  private static final Instant START = Instant.parse("2026-11-20T01:00:00Z");

  private final MutableClock clock = new MutableClock(START);
  private final LoginAttemptLimiter limiter =
      new LoginAttemptLimiter(
          clock, new LoginAttemptProperties(5, Duration.ofMinutes(15), Duration.ofMinutes(15)));

  @Test
  void 失敗が4回まではロックしない() {
    fail(4);

    assertNotLocked(EMAIL);
  }

  @Test
  void 失敗が5回でロックする() {
    fail(5);

    assertLocked(EMAIL);
  }

  @Test
  void ロックは15分ちょうどで解ける_その1秒前はロック中() {
    fail(5);

    clock.set(START.plus(Duration.ofMinutes(15)).minusSeconds(1));
    assertLocked(EMAIL);

    clock.set(START.plus(Duration.ofMinutes(15)));
    assertNotLocked(EMAIL);
  }

  @Test
  void ロックが解けたら_数え直す() {
    fail(5);
    clock.advance(Duration.ofMinutes(15));

    fail(4);

    assertNotLocked(EMAIL);
  }

  @Test
  void 最初の失敗から15分たったら_数え直す() {
    fail(4);
    clock.advance(Duration.ofMinutes(15));

    fail(1);

    assertNotLocked(EMAIL);
  }

  @Test
  void 最初の失敗から15分より前の5回目でロックする() {
    fail(4);
    clock.advance(Duration.ofMinutes(15).minusSeconds(1));

    fail(1);

    assertLocked(EMAIL);
  }

  @Test
  void ログインに成功したら_数え直す() {
    fail(4);
    limiter.recordSuccess(EMAIL);

    fail(4);

    assertNotLocked(EMAIL);
  }

  @Test
  void ほかのメールアドレスには影響しない() {
    fail(5);

    assertNotLocked("sato@example.com");
  }

  /** ロック中に届いた失敗（ほぼ同時のリクエスト）で、ロックの時間を延ばさない */
  @Test
  void ロック中の失敗でロックの時間を延ばさない() {
    fail(5);
    clock.advance(Duration.ofMinutes(10));
    limiter.recordFailure(EMAIL);

    clock.set(START.plus(Duration.ofMinutes(15)));

    assertNotLocked(EMAIL);
  }

  @Test
  void 期限の過ぎた記録を消し_ロック中と数えている途中の記録は残す() {
    fail(5);
    limiter.recordFailure("sato@example.com");
    clock.advance(Duration.ofMinutes(14));
    limiter.recordFailure("suzuki@example.com");

    clock.set(START.plus(Duration.ofMinutes(15)));
    limiter.removeExpired();

    // 山田（ロックが解けた）と佐藤（数える期間が過ぎた）は消え、鈴木（数えている途中）は残る
    assertThat(limiter.trackedCount()).isEqualTo(1);
  }

  private void fail(int times) {
    for (int i = 0; i < times; i++) {
      limiter.recordFailure(EMAIL);
    }
  }

  private void assertLocked(String email) {
    assertThatThrownBy(() -> limiter.checkNotLocked(email))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.toApiError().code()).isEqualTo("TOO_MANY_LOGIN_ATTEMPTS"));
  }

  private void assertNotLocked(String email) {
    assertThatCode(() -> limiter.checkNotLocked(email)).doesNotThrowAnyException();
  }
}
