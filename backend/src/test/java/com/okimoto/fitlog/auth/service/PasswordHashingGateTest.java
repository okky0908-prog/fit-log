package com.okimoto.fitlog.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.okimoto.fitlog.auth.config.PasswordHashingProperties;
import com.okimoto.fitlog.common.controller.ApiException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** パスワードのハッシュ・照合の同時実行の制限（docs/test-plan.md 4.8。同時に動かせる数を1にした設定） */
class PasswordHashingGateTest {

  private final PasswordHashingGate gate =
      new PasswordHashingGate(new PasswordHashingProperties(1, Duration.ofMillis(200)));

  @Test
  void 空いていれば実行して結果を返す() {
    assertThat(gate.run(() -> "hash")).isEqualTo("hash");
  }

  @Test
  void 一つ目が動いている間の二つ目は_待ちきれずに503のSERVER_BUSYで_実行しない() throws Exception {
    CountDownLatch running = new CountDownLatch(1);
    CountDownLatch finish = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
      Future<String> first =
          executor.submit(() ->
              gate.run(() -> {
                running.countDown();
                await(finish);
                return "first";
              }));
      assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();

      AtomicBoolean secondRan = new AtomicBoolean();
      assertThatThrownBy(() -> gate.run(() -> secondRan.getAndSet(true)))
          .isInstanceOfSatisfying(
              ApiException.class, e -> assertThat(e.toApiError().code()).isEqualTo("SERVER_BUSY"));
      assertThat(secondRan).isFalse();

      finish.countDown();
      assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("first");
    }
    // 一つ目が終われば、次は動ける
    assertThat(gate.run(() -> "third")).isEqualTo("third");
  }

  @Test
  void 処理が例外で終わっても_空きを返す() {
    assertThatThrownBy(() ->
            gate.run(() -> {
              throw new IllegalStateException("失敗");
            }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(gate.run(() -> "next")).isEqualTo("next");
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
