package com.okimoto.fitlog.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.okimoto.fitlog.mail.config.MailSendProperties;
import com.okimoto.fitlog.mail.repository.MailSendLogRepository;
import com.okimoto.fitlog.mail.repository.MailType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * メールの送信の上限（docs/test-plan.md 4.8。同じアドレスに1時間で5通まで）。
 *
 * <p>「直近1時間」の境界（ちょうど1時間前の送信は数えない）は、MailSendLogRepositoryTest で本物の DB で確かめる
 */
class MailSendLimiterTest {

  private static final Instant NOW = Instant.parse("2026-11-20T01:00:00Z");
  private static final OffsetDateTime NOW_ODT = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
  private static final String EMAIL = "yamada@example.com";

  private final MailSendLogRepository repository = mock(MailSendLogRepository.class);
  private final MailSendLimiter limiter =
      new MailSendLimiter(
          repository,
          new MailSendProperties(
              "noreply@fitlog.example", 5, Duration.ofHours(1), Duration.ofDays(1)),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void 直近1時間に4通なら_送ってよく_送信を記録する() {
    given(repository.countSentAfter(EMAIL, NOW_ODT.minusHours(1))).willReturn(4);

    assertThat(limiter.tryRecord(EMAIL, MailType.VERIFY_EMAIL)).isTrue();

    verify(repository).insert(EMAIL, MailType.VERIFY_EMAIL, NOW_ODT);
  }

  @Test
  void 直近1時間に5通なら_送らず_記録もしない() {
    given(repository.countSentAfter(EMAIL, NOW_ODT.minusHours(1))).willReturn(5);

    assertThat(limiter.tryRecord(EMAIL, MailType.VERIFY_EMAIL)).isFalse();

    verify(repository, never()).insert(any(), any(), any());
  }

  /** 数える前にロックを取る（同時に2通送るときに、どちらも4通と数えないように） */
  @Test
  void 数える前に送り先のアドレスでロックする() {
    limiter.tryRecord(EMAIL, MailType.VERIFY_EMAIL);

    InOrder order = inOrder(repository);
    order.verify(repository).lockAddress(EMAIL);
    order.verify(repository).countSentAfter(any(), any());
  }

  @Test
  void 送信の記録は1日より古いものを消す() {
    given(repository.deleteSentBefore(NOW_ODT.minusDays(1))).willReturn(3);

    assertThat(limiter.deleteOldLogs()).isEqualTo(3);
  }
}
