package com.okimoto.fitlog.notification.service;

import com.okimoto.fitlog.notification.repository.NewNotification;
import com.okimoto.fitlog.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * 通知を作る（F-110、docs/test-plan.md 5.9）。保存の確定後に {@link NotificationListener} から呼ばれる。
 *
 * <ul>
 *   <li>自分の操作で自分に通知は作らない（DB の CHECK でも止める）
 *   <li>作る直前に、その種類の {@link NotificationGuard} で、通知がまだ正しいかを確かめる（フォローの直後に解除したときなど）
 *   <li>作れなくても、もとの操作は成功のまま。失敗はログに残す（通知が1件届かない程度で済むため。docs/tech-stack.md 3.6）
 * </ul>
 */
@Component
public class NotificationCreator {

  private static final Logger log = LoggerFactory.getLogger(NotificationCreator.class);

  private final NotificationRepository repository;
  private final List<NotificationGuard> guards;
  private final TransactionOperations transactions;
  private final Clock clock;

  public NotificationCreator(
      NotificationRepository repository,
      List<NotificationGuard> guards,
      TransactionOperations transactions,
      Clock clock) {
    this.repository = repository;
    this.guards = List.copyOf(guards);
    this.transactions = transactions;
    this.clock = clock;
  }

  public void create(NotificationEvent event) {
    if (event.actorId() != null && event.actorId() == event.recipientId()) {
      return;
    }
    try {
      // 確かめる役が読んだ行のロックを、通知を作り終えるまで持つため、1つのトランザクションにする
      transactions.executeWithoutResult(status -> {
        if (isStillValid(event)) {
          repository.insert(
              new NewNotification(
                  event.recipientId(),
                  event.actorId(),
                  event.type(),
                  event.workoutId(),
                  OffsetDateTime.now(clock)));
        }
      });
    } catch (RuntimeException e) {
      log.warn("通知を作れませんでした（{}）", event, e);
    }
  }

  private boolean isStillValid(NotificationEvent event) {
    return guards.stream()
        .filter(guard -> guard.supports(event.type()))
        .allMatch(guard -> guard.isStillValid(event));
  }
}
