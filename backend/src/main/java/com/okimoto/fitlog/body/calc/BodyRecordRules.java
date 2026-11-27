package com.okimoto.fitlog.body.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 体重の記録の入力チェックと kg への換算（docs/features.md「体重の記録」、docs/database.md「単位の換算と小数の桁」）。
 *
 * <p>範囲と刻みは、入力した単位で確かめる（kg は 20.0〜300.0 の 0.1 刻み、lb は 44.0〜661.0 の 0.2 刻み）。体重の目標の目標値も同じ決まり
 */
public final class BodyRecordRules {

  static final String KG_WEIGHT = "体重は20.0〜300.0kgの範囲で、0.1kg刻みで入力してください";
  static final String LB_WEIGHT = "体重は44.0〜661.0lbの範囲で、0.2lb刻みで入力してください";
  static final String BODY_FAT = "体脂肪率は1.0〜75.0%の範囲で、0.1%刻みで入力してください";
  static final String UNIT = "体重の単位を選んでください";

  private static final BigDecimal KG_PER_LB = new BigDecimal("0.45359237");

  /** 保存する体重の小数の桁 */
  private static final int STORED_SCALE = 3;

  private BodyRecordRules() {}

  /**
   * 体重・単位・体脂肪率（任意）のチェック。すべての項目のエラーを集める。単位が正しくなければ、体重の範囲は確かめない
   *
   * @return 項目（weight・weightUnit・bodyFatPct）ごとのエラーの文言。なければ空
   */
  public static Map<String, String> check(
      BigDecimal weight, String weightUnit, BigDecimal bodyFatPct) {
    Map<String, String> errors = new LinkedHashMap<>();
    if ("kg".equals(weightUnit)) {
      if (!inSteps(weight, "20.0", "300.0", "0.1")) {
        errors.put("weight", KG_WEIGHT);
      }
    } else if ("lb".equals(weightUnit)) {
      if (!inSteps(weight, "44.0", "661.0", "0.2")) {
        errors.put("weight", LB_WEIGHT);
      }
    } else {
      errors.put("weightUnit", UNIT);
    }
    if (bodyFatPct != null && !inSteps(bodyFatPct, "1.0", "75.0", "0.1")) {
      errors.put("bodyFatPct", BODY_FAT);
    }
    return errors;
  }

  /** {@link #check} を通った体重を、保存する kg（小数第3位に四捨五入）にする */
  public static BigDecimal toKg(BigDecimal weight, String weightUnit) {
    BigDecimal kg = "lb".equals(weightUnit) ? weight.multiply(KG_PER_LB) : weight;
    return kg.setScale(STORED_SCALE, RoundingMode.HALF_UP);
  }

  /**
   * 推移（A-103）の期間の始まりの日（この日を含む）。{@code 3m}・{@code 1y} 以外は空
   *
   * @param today 今日（日本時間）
   */
  public static Optional<LocalDate> trendFrom(String range, LocalDate today) {
    return switch (range) {
      case "3m" -> Optional.of(today.minusMonths(3));
      case "1y" -> Optional.of(today.minusYears(1));
      default -> Optional.empty();
    };
  }

  /** min〜max の範囲で、step の倍数か */
  private static boolean inSteps(BigDecimal value, String min, String max, String step) {
    return value != null
        && value.compareTo(new BigDecimal(min)) >= 0
        && value.compareTo(new BigDecimal(max)) <= 0
        && value.remainder(new BigDecimal(step)).signum() == 0;
  }
}
