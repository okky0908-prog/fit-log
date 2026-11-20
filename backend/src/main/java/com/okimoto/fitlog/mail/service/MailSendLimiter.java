package com.okimoto.fitlog.mail.service;

import com.okimoto.fitlog.mail.config.MailSendProperties;
import com.okimoto.fitlog.mail.repository.MailSendLogRepository;
import com.okimoto.fitlog.mail.repository.MailType;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** メールの送信の上限（同じアドレスに1時間で5通まで。docs/database.md mail_send_logs）。確認メールとリセットメールは、同じアドレスなら合わせて数える */
@Service
public class MailSendLimiter {

  private final MailSendLogRepository repository;
  private final MailSendProperties properties;
  private final Clock clock;

  public MailSendLimiter(
      MailSendLogRepository repository, MailSendProperties properties, Clock clock) {
    this.repository = repository;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * 上限を超えなければ、送信を記録して true を返す。超えるなら何も記録せず false を返す。
   *
   * <p>呼ぶ側のトランザクションの中で呼ぶ（送り先のアドレスのロックは、トランザクションの終わりまで持つ。数えてから記録するまでの間に、同じアドレスへの送信を割り込ませないため）
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean tryRecord(String email, MailType type) {
    OffsetDateTime now = OffsetDateTime.now(clock);
    repository.lockAddress(email);
    if (repository.countSentAfter(email, now.minus(properties.window()))
        >= properties.maxPerWindow()) {
      return false;
    }
    repository.insert(email, type, now);
    return true;
  }

  /**
   * 残す期間（1日）より古い送信の記録を消す（毎日の掃除）
   *
   * @return 消した件数
   */
  public int deleteOldLogs() {
    return repository.deleteSentBefore(OffsetDateTime.now(clock).minus(properties.logRetention()));
  }
}
