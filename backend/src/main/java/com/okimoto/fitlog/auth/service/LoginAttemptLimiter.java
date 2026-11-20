package com.okimoto.fitlog.auth.service;

import com.okimoto.fitlog.auth.config.LoginAttemptProperties;
import com.okimoto.fitlog.common.controller.ApiException;
import com.okimoto.fitlog.common.controller.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ログインの試行回数の制限（総当たりの対策。前回と同じ）。
 *
 * <p>メールアドレスごとに、最初の失敗から {@code window} の間の失敗を数え、{@code maxFailures} 回になったら {@code lockDuration}
 * の間ロックする。登録されていないメールアドレスも同じように数える（ロックされるかどうかで、登録の有無を探られないように）。
 *
 * <p>記録はアプリのメモリに持つ。API のタスクは1つなので、これで足りる。タスクを増やすときは、記録を DB などの共有の場所に移す。
 * 再起動すると記録は消える（ロックが解けるだけで、ログインできなくなることはない）
 */
@Component
public class LoginAttemptLimiter {

  private final Clock clock;
  private final LoginAttemptProperties properties;
  private final ConcurrentMap<String, Attempts> attempts = new ConcurrentHashMap<>();

  public LoginAttemptLimiter(Clock clock, LoginAttemptProperties properties) {
    this.clock = clock;
    this.properties = properties;
  }

  /**
   * ロック中なら 429 にする。パスワードを照合する前に呼ぶ。
   *
   * @param email そろえたメールアドレス（小文字・前後の空白なし）
   */
  public void checkNotLocked(String email) {
    Attempts current = attempts.get(email);
    if (current != null && current.isLocked(clock.instant())) {
      throw new ApiException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
    }
  }

  /** 失敗を数える。この失敗で上限に届いたら、ロックを始める */
  public void recordFailure(String email) {
    Instant now = clock.instant();
    attempts.compute(
        email,
        (key, current) -> {
          if (current != null && current.isLocked(now)) {
            // ロック中（ほぼ同時のリクエスト）。ロックの時間は延ばさない
            return current;
          }
          return current == null || current.isExpired(now, properties)
              ? Attempts.first(now, properties)
              : current.next(now, properties);
        });
  }

  /** ログインに成功したら、それまでの失敗の回数を消す */
  public void recordSuccess(String email) {
    attempts.remove(email);
  }

  /** ロックも数える期間も過ぎた記録を消す（メモリに残り続けないように。10分ごと） */
  @Scheduled(fixedDelayString = "PT10M")
  void removeExpired() {
    Instant now = clock.instant();
    attempts.values().removeIf(a -> !a.isLocked(now) && a.isExpired(now, properties));
  }

  int trackedCount() {
    return attempts.size();
  }

  /**
   * 1つのメールアドレスの記録。
   *
   * @param failures 数えている期間の中の失敗の回数
   * @param firstFailureAt 数えている期間の最初の失敗の時刻
   * @param lockedUntil ロックが解ける時刻（ロックしていなければ null）
   */
  private record Attempts(int failures, Instant firstFailureAt, Instant lockedUntil) {

    static Attempts first(Instant now, LoginAttemptProperties properties) {
      return new Attempts(0, now, null).next(now, properties);
    }

    Attempts next(Instant now, LoginAttemptProperties properties) {
      int count = failures + 1;
      Instant lockEnd =
          count >= properties.maxFailures() ? now.plus(properties.lockDuration()) : null;
      return new Attempts(count, firstFailureAt, lockEnd);
    }

    boolean isLocked(Instant now) {
      return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** ロックが解けた、または最初の失敗から数える期間が過ぎた */
    boolean isExpired(Instant now, LoginAttemptProperties properties) {
      if (lockedUntil != null) {
        return !now.isBefore(lockedUntil);
      }
      return !now.isBefore(firstFailureAt.plus(properties.window()));
    }
  }
}
