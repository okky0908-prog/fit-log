package com.okimoto.fitlog.mail.repository;

import static com.okimoto.fitlog.jooq.Tables.MAIL_SEND_LOGS;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.val;

import java.time.OffsetDateTime;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/** メールの送信の記録（mail_send_logs）の読み書き。日時はすべて引数で受け取る（SQL の now() を使わない） */
@Repository
public class MailSendLogRepository {

  private final DSLContext dsl;

  public MailSendLogRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * 送り先のアドレスでロックする（トランザクションの終わりで外れる）。同じアドレスに同時に2通送るとき、どちらも「まだ4通」と数えて6通になるのを防ぐ。
   * 登録されていないアドレスにも送るので、users の行のロックではなく、アドレスの文字列から作った番号でロックする（docs/database.md mail_send_logs）
   */
  public void lockAddress(String email) {
    dsl.select(field("pg_advisory_xact_lock(hashtext({0}))", val(email))).fetch();
  }

  /** そのアドレスに、指定した日時より後（その日時ちょうどは含まない）に送った数 */
  public int countSentAfter(String email, OffsetDateTime since) {
    return dsl.fetchCount(
        MAIL_SEND_LOGS, MAIL_SEND_LOGS.EMAIL.eq(email).and(MAIL_SEND_LOGS.SENT_AT.gt(since)));
  }

  public void insert(String email, MailType type, OffsetDateTime sentAt) {
    dsl.insertInto(MAIL_SEND_LOGS)
        .set(MAIL_SEND_LOGS.EMAIL, email)
        .set(MAIL_SEND_LOGS.MAIL_TYPE, type.value())
        .set(MAIL_SEND_LOGS.SENT_AT, sentAt)
        .execute();
  }

  /** 指定した日時より前（その日時ちょうどは含まない）の記録を消し、消した件数を返す */
  public int deleteSentBefore(OffsetDateTime threshold) {
    return dsl.deleteFrom(MAIL_SEND_LOGS).where(MAIL_SEND_LOGS.SENT_AT.lt(threshold)).execute();
  }
}
