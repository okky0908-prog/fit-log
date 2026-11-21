package com.okimoto.fitlog.exercise.calc;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;

/** 種目の決まり（docs/database.md「exercises」） */
public final class ExerciseRules {

  /** LIKE で、そのままの文字として扱うための前置きの文字 */
  public static final char LIKE_ESCAPE = '\\';

  private static final BigDecimal STRENGTH_METS = new BigDecimal("3.5");
  private static final BigDecimal BODYWEIGHT_METS = new BigDecimal("3.8");
  private static final BigDecimal CARDIO_METS = new BigDecimal("6.0");

  private ExerciseRules() {}

  /**
   * 同じ名前かどうかの判定と検索に使う名前（name_key）。前後の空白を除き、NFKC で正規化して（全角英数字・半角カナをそろえる）、英字を小文字にする。 マイグレーションの初期データの
   * {@code LOWER(NORMALIZE(name, NFKC))} と同じ結果になる
   */
  public static String nameKey(String name) {
    return Normalizer.normalize(name.strip(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
  }

  /** 名前の部分一致の LIKE のパターン。検索の文字を name_key と同じにそろえ、{@code % _ \} はそのままの文字として扱う */
  public static String containsPattern(String query) {
    StringBuilder pattern = new StringBuilder("%");
    for (char c : nameKey(query).toCharArray()) {
      if (c == '%' || c == '_' || c == LIKE_ESCAPE) {
        pattern.append(LIKE_ESCAPE);
      }
      pattern.append(c);
    }
    return pattern.append('%').toString();
  }

  /** 自分専用の種目の METs の標準の値（docs/database.md「自分専用の種目の METs」） */
  public static BigDecimal defaultMets(String bodyPart, String recordType) {
    return switch (recordType) {
      case "reps" -> BODYWEIGHT_METS;
      case "time_distance" -> "cardio".equals(bodyPart) ? CARDIO_METS : STRENGTH_METS;
      default -> STRENGTH_METS;
    };
  }
}
