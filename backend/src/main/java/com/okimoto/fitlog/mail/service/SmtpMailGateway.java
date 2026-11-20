package com.okimoto.fitlog.mail.service;

import com.okimoto.fitlog.mail.config.MailSendProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** SMTP でメールを送る（手元の開発とテストは Mailpit。application.yml の spring.mail） */
@Service
public class SmtpMailGateway implements MailGateway {

  private final JavaMailSender mailSender;
  private final MailSendProperties properties;

  public SmtpMailGateway(JavaMailSender mailSender, MailSendProperties properties) {
    this.mailSender = mailSender;
    this.properties = properties;
  }

  @Override
  public void send(String to, String subject, String text) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(properties.from());
    message.setTo(to);
    message.setSubject(subject);
    message.setText(text);
    mailSender.send(message);
  }
}
