package com.okimoto.fitlog.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 「今」を返す時計。日時はすべてこの時計から求め、SQL の中で DB の時刻（now()）を使わない（docs/test-plan.md 4.4）。
 * テストでは固定した時計・進められる時計に差し替える
 */
@Configuration
public class ClockConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
