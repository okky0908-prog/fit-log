package com.okimoto.fitlog.notification.service;

import com.okimoto.fitlog.notification.repository.NewNotification;
import com.okimoto.fitlog.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 通知を作る（F-110、docs/test-plan.md 5.9）。保存の確定後に {@link NotificationListener} から呼ばれる。
 *
 * <ul>
 *   <li>自分の操作で自分に通知は作らない（DB の CHECK でも止める）
 *   <li>作れなくても、もとの操作は成功のまま。失敗はログに残す（通知が1件届かない程度で済むため。docs/tech-stack.md 3.6）
 * </ul>
 */
@Component
public class NotificationCreator {

  private static final Logger log = LoggerFactory.getLogger(NotificationCreator.class);

  private final NotificationRepository repository;
  private final Clock clock;

  public NotificationCreator(NotificationRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  public void create(NotificationEvent event) {
    if (event.actorId() != null && event.actorId() == event.recipientId()) {
      return;
    }
    try {
      repository.insert(
          new NewNotification(
              event.recipientId(),
              event.actorId(),
              event.type(),
              event.workoutId(),
              OffsetDateTime.now(clock)));
    } catch (RuntimeException e) {
      log.warn("通知を作れませんでした（{}）", event, e);
    }
  }
}
