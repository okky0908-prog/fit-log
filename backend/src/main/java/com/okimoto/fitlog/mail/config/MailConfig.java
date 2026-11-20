package com.okimoto.fitlog.mail.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** メールの送信の設定値を読む */
@Configuration
@EnableConfigurationProperties(MailSendProperties.class)
public class MailConfig {}
