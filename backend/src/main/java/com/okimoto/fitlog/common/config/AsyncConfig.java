package com.okimoto.fitlog.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 保存の確定後の処理（確認メールの送信など）を、別のスレッドで動かす（{@code @Async}。docs/tech-stack.md 3.6）。
 * spring.threads.virtual.enabled により、Spring Boot が用意する実行役は仮想スレッドを使う
 */
@Configuration
@EnableAsync
public class AsyncConfig {}
