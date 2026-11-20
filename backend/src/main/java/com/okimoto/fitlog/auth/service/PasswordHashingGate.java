package com.okimoto.fitlog.auth.service;

import com.okimoto.fitlog.auth.config.PasswordHashingProperties;
import com.okimoto.fitlog.common.controller.ApiException;
import com.okimoto.fitlog.common.controller.ErrorCode;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * パスワードのハッシュ・照合（BCrypt）を同時に動かす数を絞る（前回と同じ）。
 *
 * <p>BCrypt は CPU だけを使う重い処理。ログインが集中して BCrypt が CPU を取り合うと、ログイン以外の API まで止まる（前回の性能試験で分かった）。
 * 同時に動かす数を絞り、空きを {@code maxWait} だけ待っても空かなければ、503（{@link ErrorCode#SERVER_BUSY}）ですぐに返して、ほかの API の
 * CPU を残す
 */
@Component
public class PasswordHashingGate {

  private final Semaphore permits;
  private final Duration maxWait;

  public PasswordHashingGate(PasswordHashingProperties properties) {
    // 待った順にする。あとから来たものに追い越され続けて、時間切れになることがないように
    this.permits = new Semaphore(properties.maxConcurrent(), true);
    this.maxWait = properties.maxWait();
  }

  /** 空きを待ってから task を実行する。待つ時間を過ぎても空かなければ、task を実行せずに 503 にする */
  public <T> T run(Supplier<T> task) {
    if (!tryAcquire()) {
      throw new ApiException(ErrorCode.SERVER_BUSY);
    }
    try {
      return task.get();
    } finally {
      permits.release();
    }
  }

  private boolean tryAcquire() {
    try {
      return permits.tryAcquire(maxWait.toNanos(), TimeUnit.NANOSECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
