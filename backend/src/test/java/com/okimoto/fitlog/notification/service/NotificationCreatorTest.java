package com.okimoto.fitlog.notification.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.okimoto.fitlog.notification.repository.NewNotification;
import com.okimoto.fitlog.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** 通知を作るか（docs/test-plan.md 5.9）。イベントを受け取るクラスを直接呼ぶ（別のスレッド・待ちは結合テストで確かめる） */
class NotificationCreatorTest {

  private static final Instant NOW = Instant.parse("2026-11-23T01:00:00Z");

  private NotificationRepository repository;
  private NotificationCreator creator;

  @BeforeEach
  void setUp() {
    repository = mock(NotificationRepository.class);
    creator = new NotificationCreator(repository, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void ほかの人の操作なら_受け取る人への通知を今の日時で作る() {
    creator.create(new NotificationEvent("like", 1L, 2L, 30L));

    verify(repository)
        .insert(
            new NewNotification(
                1L, 2L, "like", 30L, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC)));
  }

  @Test
  void 自分の操作で自分への通知は作らない() {
    creator.create(new NotificationEvent("comment", 1L, 1L, 30L));

    verify(repository, never()).insert(any());
  }

  @Test
  void 操作した人がいない通知_目標の達成_は作る() {
    creator.create(new NotificationEvent("goal_achieved", 1L, null, null));

    verify(repository)
        .insert(
            new NewNotification(
                1L, null, "goal_achieved", null, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC)));
  }

  @Test
  void 作れなくても_例外を外に出さない_もとの操作は成功のまま() {
    when(repository.insert(any())).thenThrow(new DataIntegrityViolationException("相手が退会した"));

    assertThatCode(() -> creator.create(new NotificationEvent("follow", 1L, 2L, null)))
        .doesNotThrowAnyException();
  }
}
