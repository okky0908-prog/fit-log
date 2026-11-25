package com.okimoto.fitlog.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.okimoto.fitlog.comment.repository.CommentRepository;
import com.okimoto.fitlog.notification.service.NotificationEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** コメント・返信の通知が、まだ正しいか（書いてすぐ消したコメントの通知は作らない） */
class CommentNotificationGuardTest {

  private final CommentRepository repository = mock(CommentRepository.class);
  private final CommentNotificationGuard guard = new CommentNotificationGuard(repository);

  @ParameterizedTest(name = "{0} → {1}")
  @CsvSource({"comment, true", "reply, true", "like, false", "follow, false"})
  void コメントと返信の通知だけを確かめる(String type, boolean expected) {
    assertThat(guard.supports(type)).isEqualTo(expected);
  }

  @ParameterizedTest(name = "残っている {0} → {0}")
  @CsvSource({"true", "false"})
  void 書かれたコメントが残っていれば正しい(boolean exists) {
    when(repository.lockExists(500L)).thenReturn(exists);

    assertThat(guard.isStillValid(new NotificationEvent("reply", 1L, 2L, 30L, 500L)))
        .isEqualTo(exists);
  }
}
