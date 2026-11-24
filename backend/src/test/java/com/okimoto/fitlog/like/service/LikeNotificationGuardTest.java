package com.okimoto.fitlog.like.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.okimoto.fitlog.like.repository.LikeRepository;
import com.okimoto.fitlog.notification.service.NotificationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** ナイストレーニングの通知が、まだ正しいか（docs/test-plan.md 5.9「取り消し」） */
class LikeNotificationGuardTest {

  private final LikeRepository repository = mock(LikeRepository.class);
  private final LikeNotificationGuard guard = new LikeNotificationGuard(repository);

  @Test
  void ナイストレーニングの通知だけを確かめる() {
    assertThat(guard.supports("like")).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"comment", "reply", "follow", "goal_cheer"})
  void ほかの種類は確かめない(String type) {
    assertThat(guard.supports(type)).isFalse();
  }

  @ParameterizedTest(name = "付いている {0} → {0}")
  @CsvSource({"true", "false"})
  void 操作した人のナイストレーニングがその記録に残っていれば正しい(boolean exists) {
    // 操作した人（2）が、記録（30）に付けた。受け取る人（1）は投稿者
    when(repository.lockExists(30L, 2L)).thenReturn(exists);

    assertThat(guard.isStillValid(new NotificationEvent("like", 1L, 2L, 30L))).isEqualTo(exists);
  }
}
