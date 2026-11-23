package com.okimoto.fitlog.notification.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 通知のイベントを受け取り、通知を作る（docs/tech-stack.md 3.6）。
 *
 * <ul>
 *   <li>もとの操作の保存が確定したあとに動く（{@code @TransactionalEventListener} の既定は AFTER_COMMIT）。取り消されたら作らない
 *   <li>別のスレッドで動く（{@code @Async}）。もとの操作の応答を待たせない
 * </ul>
 *
 * 通知を作る操作（フォロー・ナイストレーニング・コメントなど）は、保存の処理の中で {@link NotificationEvent} を出すだけでよい
 */
@Component
public class NotificationListener {

  private final NotificationCreator creator;

  public NotificationListener(NotificationCreator creator) {
    this.creator = creator;
  }

  @Async
  @TransactionalEventListener
  public void onEvent(NotificationEvent event) {
    creator.create(event);
  }
}
