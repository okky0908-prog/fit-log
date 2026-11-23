package com.okimoto.fitlog.common.calc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Optional;

/**
 * 一覧のカーソル（docs/api.md「ページング」）。並び順の列（日時と ID）を、画面が解釈しない文字列にする。
 *
 * <p>中身は「日時（UTC、マイクロ秒まで）|ID」を URL に入れられる Base64 にしたもの。壊れていれば読めない（呼ぶ側で 400 にする）
 */
public final class Cursor {

  private static final String SEPARATOR = "|";

  private Cursor() {}

  /**
   * カーソルの位置。この位置より後（古い）ものから続きを返す。
   *
   * @param createdAt 日時
   * @param id ID（同じ日時の並び順を決める）
   */
  public record Position(OffsetDateTime createdAt, long id) {}

  public static String encode(OffsetDateTime createdAt, long id) {
    String payload = createdAt.toInstant() + SEPARATOR + id;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  }

  /** カーソルを読む。形が違えば空 */
  public static Optional<Position> decode(String cursor) {
    try {
      String payload = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      String[] parts = payload.split("\\|", -1);
      if (parts.length != 2) {
        return Optional.empty();
      }
      Instant createdAt = Instant.parse(parts[0]);
      long id = Long.parseLong(parts[1]);
      return id > 0
          ? Optional.of(new Position(OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC), id))
          : Optional.empty();
    } catch (IllegalArgumentException | DateTimeParseException e) {
      // Base64 として読めない・日時や数として読めない（NumberFormatException は IllegalArgumentException の仲間）
      return Optional.empty();
    }
  }
}
