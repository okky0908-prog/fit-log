package com.okimoto.fitlog.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 決まった時刻・間隔で動く処理（期限切れのリフレッシュトークンの掃除など）を動かす */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
