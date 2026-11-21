package com.okimoto.fitlog.common.calc;

import static org.assertj.core.api.Assertions.assertThat;

import com.okimoto.fitlog.support.MutableClock;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** 日本時間の「今日」と日時（docs/test-plan.md 4.4） */
class JapanTimeTest {

  @Test
  void 今日は日本時間で決まる_UTCの15時が日付の境目() {
    // 日本時間 2026-11-14 23:59:59 ＝ UTC 14:59:59
    assertThat(JapanTime.today(at("2026-11-14T14:59:59Z"))).isEqualTo(LocalDate.of(2026, 11, 14));
    // 日本時間 2026-11-15 00:00:00 ＝ UTC 15:00:00
    assertThat(JapanTime.today(at("2026-11-14T15:00:00Z"))).isEqualTo(LocalDate.of(2026, 11, 15));
  }

  @Test
  void 時計のタイムゾーンによらず日本時間で決まる() {
    // テスト用の時計は withZone でタイムゾーンを変えない（UTC のまま）
    Clock utcOnly = new MutableClock(Instant.parse("2026-11-14T15:00:00Z"));

    assertThat(JapanTime.today(utcOnly)).isEqualTo(LocalDate.of(2026, 11, 15));
  }

  @Test
  void 日時を日本時間のオフセットにそろえる() {
    OffsetDateTime utc = OffsetDateTime.of(2026, 11, 14, 10, 30, 0, 0, ZoneOffset.UTC);

    assertThat(JapanTime.of(utc)).hasToString("2026-11-14T19:30+09:00");
  }

  @Test
  void 日時がなければnull() {
    assertThat(JapanTime.of(null)).isNull();
  }

  private static Clock at(String instant) {
    return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
  }
}
