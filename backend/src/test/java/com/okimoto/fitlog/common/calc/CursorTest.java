package com.okimoto.fitlog.common.calc;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import org.junit.jupiter.api.Nested;
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

  /** プロフィールの記録一覧（A-81）は、実施日・投稿日時・ID の順に並べるので、実施日も入れる */
  @Nested
  class 実施日つき {

    @Test
    void 作ったカーソルを読むと_同じ実施日と日時とIDに戻る() {
      OffsetDateTime createdAt = OffsetDateTime.parse("2026-11-23T10:15:00.123456+09:00");

      Cursor.DatedPosition position =
          Cursor.decodeDated(Cursor.encodeDated(LocalDate.of(2026, 11, 20), createdAt, 123L))
              .orElseThrow();

      assertThat(position.performedOn()).isEqualTo(LocalDate.of(2026, 11, 20));
      assertThat(position.createdAt()).isAtSameInstantAs(createdAt);
      assertThat(position.createdAt().getNano()).isEqualTo(123_456_000);
      assertThat(position.id()).isEqualTo(123L);
    }

    @Test
    void 実施日のないカーソルとは取り違えない() {
      String cursor = Cursor.encode(OffsetDateTime.parse("2026-11-23T10:15:00+09:00"), 12L);

      assertThat(Cursor.decodeDated(cursor)).isEmpty();
      assertThat(
              Cursor.decode(
                  Cursor.encodeDated(
                      LocalDate.of(2026, 11, 20),
                      OffsetDateTime.parse("2026-11-23T10:15:00+09:00"),
                      12L)))
          .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "2026-11-20|2026-11-23T10:15:00Z",
          "日付でない|2026-11-23T10:15:00Z|12",
          "2026-02-30|2026-11-23T10:15:00Z|12",
          "2026-11-20|日時でない|12",
          "2026-11-20|2026-11-23T10:15:00Z|abc",
          "2026-11-20|2026-11-23T10:15:00Z|0",
          "2026-11-20|2026-11-23T10:15:00Z|12|3"
        })
    void 中身の形が違えば読めない(String payload) {
      assertThat(Cursor.decodeDated(base64(payload))).isEmpty();
    }

    @Test
    void 文字の形が違えば読めない() {
      assertThat(Cursor.decodeDated("abc$")).isEmpty();
    }
  }

  /** 体重の一覧（A-101）は、1日1件なので日付だけで位置が決まる */
  @Nested
  class 日付だけ {

    @Test
    void 作ったカーソルを読むと_同じ日付に戻る() {
      assertThat(Cursor.decodeDate(Cursor.encodeDate(LocalDate.of(2026, 11, 20))))
          .contains(LocalDate.of(2026, 11, 20));
    }

    @Test
    void ほかのカーソルとは取り違えない() {
      assertThat(
              Cursor.decodeDate(
                  Cursor.encode(OffsetDateTime.parse("2026-11-23T10:15:00+09:00"), 12L)))
          .isEmpty();
      assertThat(Cursor.decode(Cursor.encodeDate(LocalDate.of(2026, 11, 20)))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"日付でない", "2026-02-30", "2026-11-20|12", ""})
    void 中身の形が違えば読めない(String payload) {
      assertThat(Cursor.decodeDate(base64(payload))).isEmpty();
    }

    @Test
    void 文字の形が違えば読めない() {
      assertThat(Cursor.decodeDate("abc$")).isEmpty();
    }
  }

  private static String base64(String payload) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  }
}
