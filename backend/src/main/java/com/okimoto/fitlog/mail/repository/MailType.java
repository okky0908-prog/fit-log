package com.okimoto.fitlog.mail.repository;

/** メールの種類（mail_send_logs.mail_type）。送信の上限は、種類をまとめてアドレスごとに数える */
public enum MailType {
  /** メールアドレスの確認 */
  VERIFY_EMAIL("verify_email"),
  /** パスワードの再設定（段階 5） */
  PASSWORD_RESET("password_reset");

  private final String value;

  MailType(String value) {
    this.value = value;
  }

  /** DB に入れる値 */
  public String value() {
    return value;
  }
}
