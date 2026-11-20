package com.okimoto.fitlog.mail.service;

/**
 * メールを送る窓口（docs/tech-stack.md 3.8）。手元の開発とテストは SMTP で Mailpit に送る（{@link SmtpMailGateway}）。本番の
 * Amazon SES は、AWS に載せるときにこの interface の実装として足す
 */
public interface MailGateway {

  /**
   * テキストのメールを1通送る。
   *
   * @throws org.springframework.mail.MailException 送れなかったとき
   */
  void send(String to, String subject, String text);
}
