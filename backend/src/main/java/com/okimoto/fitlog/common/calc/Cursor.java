package com.okimoto.fitlog.common.calc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Optional;

/**
 * 一覧のカーソル（docs/api.md「ページング」）。並び順の列（日時と ID）を、画面が解釈しない文字列にする。
 *
 * <p>中身は「日時（UTC、マイクロ秒まで）|ID」（実施日つきは「実施日|日時|ID」）を URL に入れられる Base64 にしたもの。壊れていれば読めない（呼ぶ側で 400 にする）
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

  /**
   * 実施日つきのカーソルの位置（プロフィールの記録一覧。実施日・投稿日時・ID の新しい順）。
   *
   * @param performedOn 実施日
   * @param createdAt 投稿日時
   * @param id ID
   */
  public record DatedPosition(LocalDate performedOn, OffsetDateTime createdAt, long id) {}

  public static String encode(OffsetDateTime createdAt, long id) {
    return toBase64(createdAt.toInstant() + SEPARATOR + id);
  }

  public static String encodeDated(LocalDate performedOn, OffsetDateTime createdAt, long id) {
    return toBase64(performedOn + SEPARATOR + createdAt.toInstant() + SEPARATOR + id);
  }

  /** カーソルを読む。形が違えば空 */
  public static Optional<Position> decode(String cursor) {
    return parts(cursor, 2).flatMap(parts -> position(parts[0], parts[1]));
  }

  /** 実施日つきのカーソルを読む。形が違えば空 */
  public static Optional<DatedPosition> decodeDated(String cursor) {
    return parts(cursor, 3)
        .flatMap(parts -> {
          try {
            LocalDate performedOn = LocalDate.parse(parts[0]);
            return position(parts[1], parts[2])
                .map(p -> new DatedPosition(performedOn, p.createdAt(), p.id()));
          } catch (DateTimeParseException e) {
            return Optional.empty();
          }
        });
  }

  private static String toBase64(String payload) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  }

  /** Base64 を戻して「|」で分ける。数が違えば空 */
  private static Optional<String[]> parts(String cursor, int count) {
    try {
      String payload = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      String[] parts = payload.split("\\|", -1);
      return parts.length == count ? Optional.of(parts) : Optional.empty();
    } catch (IllegalArgumentException e) {
      // Base64 として読めない
      return Optional.empty();
    }
  }

  private static Optional<Position> position(String createdAt, String id) {
    try {
      Instant instant = Instant.parse(createdAt);
      long value = Long.parseLong(id);
      return value > 0
          ? Optional.of(new Position(OffsetDateTime.ofInstant(instant, ZoneOffset.UTC), value))
          : Optional.empty();
    } catch (IllegalArgumentException | DateTimeParseException e) {
      // 日時や数として読めない（NumberFormatException は IllegalArgumentException の仲間）
      return Optional.empty();
    }
  }
}
