package com.okimoto.fitlog.support;

import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;

/**
 * DB に送った SQL の数を数える（docs/test-plan.md 4.7「N+1 がないこと」）。jOOQ の ExecuteListener として登録する（{@link
 * TestcontainersConfiguration}）。
 *
 * <p>{@link #count} を呼んだスレッドで実行した SQL だけを数える。MockMvc は API をテストと同じスレッドで動かすので、API の SQL
 * は数に入り、保存の確定後に別のスレッドで動く処理（通知など）の SQL は入らない（数が揺れない）
 *
 * <pre>{@code
 * int sql = sqlCounter.count(() -> mockMvc.perform(get("/api/timeline")));
 * }</pre>
 */
public final class SqlCounter implements ExecuteListener {

  private final ThreadLocal<int[]> counting = new ThreadLocal<>();

  /** 数える間に動かす処理 */
  @FunctionalInterface
  public interface Action {
    void run() throws Exception;
  }

  /** 処理を動かし、その間に送った SQL の数を返す */
  public int count(Action action) throws Exception {
    int[] counter = new int[1];
    counting.set(counter);
    try {
      action.run();
      return counter[0];
    } finally {
      counting.remove();
    }
  }

  @Override
  public void executeStart(ExecuteContext ctx) {
    int[] counter = counting.get();
    if (counter != null) {
      counter[0]++;
    }
  }
}
