package com.okimoto.fitlog.common.calc;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 一覧のカーソル（docs/api.md「ページング」）。中身は画面で解釈しない文字列で、壊れていれば読めない */
class CursorTest {

  @Test
  void 作ったカーソルを読むと_同じ日時とIDに戻る_マイクロ秒も失わない() {
    OffsetDateTime createdAt = OffsetDateTime.parse("2026-11-23T10:15:00.123456+09:00");

    Cursor.Position position = Cursor.decode(Cursor.encode(createdAt, 123L)).orElseThrow();

    assertThat(position.createdAt()).isAtSameInstantAs(createdAt);
    assertThat(position.createdAt().getNano()).isEqualTo(123_456_000);
    assertThat(position.id()).isEqualTo(123L);
  }

  @Test
  void URLにそのまま入れられる文字だけを使う() {
    String cursor = Cursor.encode(OffsetDateTime.parse("2026-11-23T10:15:00+09:00"), 999_999L);

    assertThat(cursor).matches("[A-Za-z0-9_-]+");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "abc$", "!!!"})
  void 文字の形が違えば読めない(String cursor) {
    assertThat(Cursor.decode(cursor)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "2026-11-23T10:15:00Z",
        "2026-11-23T10:15:00Z|",
        "2026-11-23T10:15:00Z|abc",
        "日時でない|12",
        "2026-11-23T10:15:00Z|12|3",
        "2026-11-23T10:15:00Z|0",
        "2026-11-23T10:15:00Z|-1"
      })
  void 中身の形が違えば読めない(String payload) {
    String cursor =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

    assertThat(Cursor.decode(cursor)).isEmpty();
  }
}
