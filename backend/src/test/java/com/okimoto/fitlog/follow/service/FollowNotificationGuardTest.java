package com.okimoto.fitlog.follow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.okimoto.fitlog.follow.repository.FollowRepository;
import com.okimoto.fitlog.notification.service.NotificationEvent;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** フォローの通知が、まだ正しいか（docs/test-plan.md 5.9「取り消し」） */
class FollowNotificationGuardTest {

  private final FollowRepository repository = mock(FollowRepository.class);
  private final FollowNotificationGuard guard = new FollowNotificationGuard(repository);

  @Test
  void フォローとリクエストの通知だけを確かめる() {
    assertThat(guard.supports("follow")).isTrue();
    assertThat(guard.supports("follow_request")).isTrue();
    assertThat(guard.supports("follow_accepted")).isFalse();
    assertThat(guard.supports("like")).isFalse();
  }

  @ParameterizedTest(name = "{0} の通知・今の状態 {1} → {2}")
  @CsvSource(
      nullValues = "null",
      value = {
        "follow,         accepted, true",
        "follow,         pending,  false",
        "follow,         null,     false",
        "follow_request, pending,  true",
        "follow_request, accepted, false",
        "follow_request, null,     false",
      })
  void 通知の種類に合う状態のフォローが残っていれば正しい(String type, String status, boolean expected) {
    // 操作した人（2）から受け取る人（1）へのフォロー
    when(repository.lockStatus(2L, 1L)).thenReturn(Optional.ofNullable(status));

    assertThat(guard.isStillValid(new NotificationEvent(type, 1L, 2L, null))).isEqualTo(expected);
  }
}
