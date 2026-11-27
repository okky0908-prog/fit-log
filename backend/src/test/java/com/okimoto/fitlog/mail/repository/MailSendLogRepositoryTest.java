package com.okimoto.fitlog.mail.repository;

import static com.okimoto.fitlog.jooq.Tables.MAIL_SEND_LOGS;
import static org.assertj.core.api.Assertions.assertThat;

import com.okimoto.fitlog.support.TestcontainersConfiguration;
import java.time.OffsetDateTime;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** メールの送信の記録の読み書き。本物の DB で確かめる（テストごとにロールバックする） */
@JooqTest
@Import({TestcontainersConfiguration.class, MailSendLogRepository.class})
@ActiveProfiles("test")
class MailSendLogRepositoryTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-20T10:00:00+09:00");
  private static final String EMAIL = "yamada@example.com";

  @Autowired private MailSendLogRepository repository;
  @Autowired private DSLContext dsl;

  /** 直近1時間：1時間ちょうど前の送信は数えない（ちょうど1時間たったら次を送れる）。1時間より1秒新しいものは数える */
  @Test
  void 指定した日時より後に送った数を_アドレスごとに数える() {
    repository.insert(EMAIL, MailType.VERIFY_EMAIL, NOW.minusHours(1));
    repository.insert(EMAIL, MailType.VERIFY_EMAIL, NOW.minusHours(1).plusSeconds(1));
    repository.insert(EMAIL, MailType.PASSWORD_RESET, NOW);
    repository.insert("sato@example.com", MailType.VERIFY_EMAIL, NOW);

    assertThat(repository.countSentAfter(EMAIL, NOW.minusHours(1))).isEqualTo(2);
  }

  @Test
  void 送り先のアドレスのロックを取れる() {
    // トランザクションの終わりで外れるロック。同じトランザクションの中なら2回取っても止まらない
    repository.lockAddress(EMAIL);
    repository.lockAddress(EMAIL);
  }

  @Test
  void 指定した日時より前の記録を消す() {
    // 表全体が対象なので、ほかの結合テスト（時計を過去に固定したもの）が残した送信の記録を先に消す（このテストの終わりにロールバックされる）
    dsl.deleteFrom(MAIL_SEND_LOGS).execute();
    repository.insert(EMAIL, MailType.VERIFY_EMAIL, NOW.minusDays(1).minusSeconds(1));
    repository.insert(EMAIL, MailType.VERIFY_EMAIL, NOW.minusDays(1));

    assertThat(repository.deleteSentBefore(NOW.minusDays(1))).isEqualTo(1);

    assertThat(repository.countSentAfter(EMAIL, NOW.minusDays(2))).isEqualTo(1);
  }
}
