package com.okimoto.fitlog.workout.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * セットの入力チェックと、kg・km への換算（docs/features.md「セットの項目」、docs/database.md「単位の換算と小数の桁」）。
 *
 * <p>範囲と刻みは、入力した単位で確かめる（kg は 0.25 刻み、lb は 0.5 刻み）
 */
public final class SetRules {

  static final String KG_WEIGHT = "重量は0.25〜500kgの範囲で、0.25kg刻みで入力してください";
  static final String LB_WEIGHT = "重量は0.5〜1100lbの範囲で、0.5lb刻みで入力してください";
  static final String WEIGHT_REPS = "回数は0〜999回で入力してください";
  static final String REPS_ONLY = "回数は1〜9999回で入力してください";
  static final String DURATION = "時間は1秒〜24時間で入力してください";
  static final String KM_DISTANCE = "距離は0.01〜500kmの範囲で、小数第2位まで入力してください";
  static final String MI_DISTANCE = "距離は0.01〜310.68マイルの範囲で、小数第2位まで入力してください";
  static final String NOT_ALLOWED = "この種目では入力できません";

  private static final BigDecimal KG_PER_LB = new BigDecimal("0.45359237");
  private static final BigDecimal KM_PER_MILE = new BigDecimal("1.609344");

  /** 保存する重量・距離の小数の桁 */
  private static final int STORED_SCALE = 3;

  private SetRules() {}

  /**
   * セットの値が、種目の記録方式に合っているか。
   *
   * @return 項目（weight・reps・durationSec・distance）ごとのエラーの文言。なければ空
   */
  public static Map<String, String> check(
      SetInput set, String recordType, String weightUnit, String distanceUnit) {
    Map<String, String> errors = new LinkedHashMap<>();
    switch (recordType) {
      case "weight_reps" -> {
        if (!validWeight(set.weight(), weightUnit)) {
          errors.put("weight", "lb".equals(weightUnit) ? LB_WEIGHT : KG_WEIGHT);
        }
        if (!integerBetween(set.reps(), 0, 999)) {
          errors.put("reps", WEIGHT_REPS);
        }
        rejectIfPresent(errors, "durationSec", set.durationSec());
        rejectIfPresent(errors, "distance", set.distance());
      }
      case "reps" -> {
        rejectIfPresent(errors, "weight", set.weight());
        if (!integerBetween(set.reps(), 1, 9999)) {
          errors.put("reps", REPS_ONLY);
        }
        rejectIfPresent(errors, "durationSec", set.durationSec());
        rejectIfPresent(errors, "distance", set.distance());
      }
      default -> {
        rejectIfPresent(errors, "weight", set.weight());
        rejectIfPresent(errors, "reps", set.reps());
        if (!integerBetween(set.durationSec(), 1, 86_400)) {
          errors.put("durationSec", DURATION);
        }
        if (set.distance() != null && !validDistance(set.distance(), distanceUnit)) {
          errors.put("distance", "mi".equals(distanceUnit) ? MI_DISTANCE : KM_DISTANCE);
        }
      }
    }
    return errors;
  }

  /** {@link #check} を通ったセットを、保存する形（kg・km・秒）にする */
  public static StoredSet toStored(SetInput set, String weightUnit, String distanceUnit) {
    return new StoredSet(
        set.weight() == null ? null : toKg(set.weight(), weightUnit),
        set.reps() == null ? null : set.reps().intValueExact(),
        set.durationSec() == null ? null : set.durationSec().intValueExact(),
        set.distance() == null ? null : toKm(set.distance(), distanceUnit));
  }

  private static boolean validWeight(BigDecimal weight, String unit) {
    if (weight == null) {
      return false;
    }
    BigDecimal step = new BigDecimal("lb".equals(unit) ? "0.5" : "0.25");
    BigDecimal max = new BigDecimal("lb".equals(unit) ? "1100" : "500");
    return weight.compareTo(step) >= 0
        && weight.compareTo(max) <= 0
        && weight.remainder(step).signum() == 0;
  }

  /** 距離は小数第2位まで。上限は km にして 500km（マイルでは 310.68） */
  private static boolean validDistance(BigDecimal distance, String unit) {
    return distance.stripTrailingZeros().scale() <= 2
        && distance.compareTo(new BigDecimal("0.01")) >= 0
        && toKm(distance, unit).compareTo(new BigDecimal("500")) <= 0;
  }

  private static boolean integerBetween(BigDecimal value, int min, int max) {
    return value != null
        && value.stripTrailingZeros().scale() <= 0
        && value.compareTo(BigDecimal.valueOf(min)) >= 0
        && value.compareTo(BigDecimal.valueOf(max)) <= 0;
  }

  private static void rejectIfPresent(Map<String, String> errors, String field, BigDecimal value) {
    if (value != null) {
      errors.put(field, NOT_ALLOWED);
    }
  }

  private static BigDecimal toKg(BigDecimal weight, String unit) {
    BigDecimal kg = "lb".equals(unit) ? weight.multiply(KG_PER_LB) : weight;
    return kg.setScale(STORED_SCALE, RoundingMode.HALF_UP);
  }

  private static BigDecimal toKm(BigDecimal distance, String unit) {
    BigDecimal km = "mi".equals(unit) ? distance.multiply(KM_PER_MILE) : distance;
    return km.setScale(STORED_SCALE, RoundingMode.HALF_UP);
  }
}
