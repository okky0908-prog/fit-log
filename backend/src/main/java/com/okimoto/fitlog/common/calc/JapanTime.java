package com.okimoto.fitlog.common.calc;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 日本時間（docs/api.md「共通のルール」）。「今日」はサーバーが日本時間で決め、日時は日本時間のオフセット（+09:00）で返す。
 *
 * <p>JVM の既定のタイムゾーンには頼らない（テストは UTC で動かして、書き漏れを見つける。docs/test-plan.md 4.4）
 */
public final class JapanTime {

  public static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

  private JapanTime() {}

  /** 日本時間の今日。時計のタイムゾーン（withZone の実装）には頼らず、時刻から求める */
  public static LocalDate today(Clock clock) {
    return clock.instant().atZone(ZONE).toLocalDate();
  }

  /** 同じ時刻を、日本時間のオフセットで表したもの。null は null のまま */
  public static OffsetDateTime of(OffsetDateTime dateTime) {
    return dateTime == null ? null : dateTime.atZoneSameInstant(ZONE).toOffsetDateTime();
  }
}
