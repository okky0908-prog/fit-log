package com.okimoto.fitlog.workout.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** セットの入力チェックと、kg・km への換算（docs/features.md「セットの項目」、docs/test-plan.md 5.1） */
class SetRulesTest {

  private static final String KG_WEIGHT = "重量は0.25〜500kgの範囲で、0.25kg刻みで入力してください";
  private static final String LB_WEIGHT = "重量は0.5〜1100lbの範囲で、0.5lb刻みで入力してください";
  private static final String WEIGHT_REPS = "回数は0〜999回で入力してください";
  private static final String REPS_ONLY = "回数は1〜9999回で入力してください";
  private static final String DURATION = "時間は1秒〜24時間で入力してください";
  private static final String KM_DISTANCE = "距離は0.01〜500kmの範囲で、小数第2位まで入力してください";
  private static final String MI_DISTANCE = "距離は0.01〜310.68マイルの範囲で、小数第2位まで入力してください";
  private static final String NOT_ALLOWED = "この種目では入力できません";

  @Nested
  class 重量と回数 {

    @ParameterizedTest
    @ValueSource(strings = {"0.25", "500", "60.5", "60.00"})
    void kgは025から500まで025刻み(String weight) {
      assertThat(check(weightReps(weight, "10"), "weight_reps", "kg", "km")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "500.25", "60.1", "-0.25"})
    void kgの範囲外と刻みに合わない値はエラー(String weight) {
      assertThat(check(weightReps(weight, "10"), "weight_reps", "kg", "km"))
          .containsExactly(entry("weight", KG_WEIGHT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.5", "1100", "135"})
    void lbは05から1100まで05刻み(String weight) {
      assertThat(check(weightReps(weight, "10"), "weight_reps", "lb", "km")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1100.5", "132.3", "0.25"})
    void lbの範囲外と刻みに合わない値はエラー(String weight) {
      assertThat(check(weightReps(weight, "10"), "weight_reps", "lb", "km"))
          .containsExactly(entry("weight", LB_WEIGHT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "999"})
    void 回数は0から999まで(String reps) {
      assertThat(check(weightReps("60", reps), "weight_reps")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1000", "10.5"})
    void 回数の範囲外と小数はエラー(String reps) {
      assertThat(check(weightReps("60", reps), "weight_reps"))
          .containsExactly(entry("reps", WEIGHT_REPS));
    }

    @Test
    void 重量と回数がなければ両方エラー_時間と距離があればエラー() {
      SetInput set = new SetInput(null, null, dec("60"), dec("1"));

      assertThat(check(set, "weight_reps"))
          .containsExactly(
              entry("weight", KG_WEIGHT),
              entry("reps", WEIGHT_REPS),
              entry("durationSec", NOT_ALLOWED),
              entry("distance", NOT_ALLOWED));
    }
  }

  @Nested
  class 回数のみ {

    @ParameterizedTest
    @ValueSource(strings = {"1", "9999"})
    void 回数は1から9999まで(String reps) {
      assertThat(check(new SetInput(null, dec(reps), null, null), "reps")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "10000", "1.5"})
    void 回数の範囲外と小数はエラー(String reps) {
      assertThat(check(new SetInput(null, dec(reps), null, null), "reps"))
          .containsExactly(entry("reps", REPS_ONLY));
    }

    @Test
    void 重量があればエラー() {
      assertThat(check(new SetInput(dec("10"), dec("10"), null, null), "reps"))
          .containsExactly(entry("weight", NOT_ALLOWED));
    }
  }

  @Nested
  class 時間と距離 {

    @ParameterizedTest
    @ValueSource(strings = {"1", "86400"})
    void 時間は1秒から24時間まで_距離はなくてもよい(String seconds) {
      assertThat(check(new SetInput(null, null, dec(seconds), null), "time_distance")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "86401", "1.5"})
    void 時間の範囲外と小数はエラー(String seconds) {
      assertThat(check(new SetInput(null, null, dec(seconds), null), "time_distance"))
          .containsExactly(entry("durationSec", DURATION));
    }

    @Test
    void 時間がなければエラー_重量と回数があればエラー() {
      assertThat(check(new SetInput(dec("60"), dec("10"), null, null), "time_distance"))
          .containsExactly(
              entry("weight", NOT_ALLOWED),
              entry("reps", NOT_ALLOWED),
              entry("durationSec", DURATION));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "500", "5.00"})
    void kmは001から500まで小数第2位まで(String distance) {
      assertThat(check(timeDistance(distance), "time_distance")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "500.01", "5.005"})
    void kmの範囲外と小数第3位がある値はエラー(String distance) {
      assertThat(check(timeDistance(distance), "time_distance"))
          .containsExactly(entry("distance", KM_DISTANCE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "310.68", "3.1"})
    void マイルは001から31068まで(String distance) {
      assertThat(check(timeDistance(distance), "time_distance", "kg", "mi")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "310.69", "1.005"})
    void マイルの範囲外と小数第3位がある値はエラー(String distance) {
      assertThat(check(timeDistance(distance), "time_distance", "kg", "mi"))
          .containsExactly(entry("distance", MI_DISTANCE));
    }
  }

  @ParameterizedTest(name = "[{index}] {0}{1} → {2}kg")
  @CsvSource({
    "60, kg, 60.000",
    "60.25, kg, 60.250",
    "135, lb, 61.235",
    "0.5, lb, 0.227",
    "1100, lb, 498.952",
  })
  void 重量はkgの小数第3位までにする_第4位で四捨五入(String weight, String unit, String kg) {
    StoredSet stored = SetRules.toStored(weightReps(weight, "10"), unit, "km");

    assertThat(stored.weightKg()).isEqualTo(new BigDecimal(kg));
    assertThat(stored.reps()).isEqualTo(10);
    assertThat(stored.durationSec()).isNull();
    assertThat(stored.distanceKm()).isNull();
  }

  @ParameterizedTest(name = "[{index}] {0}{1} → {2}km")
  @CsvSource({
    "5, km, 5.000",
    "3.1, mi, 4.989",
    "310.68, mi, 499.991",
    "0.01, mi, 0.016",
  })
  void 距離はkmの小数第3位までにする(String distance, String unit, String km) {
    StoredSet stored = SetRules.toStored(timeDistance(distance), "kg", unit);

    assertThat(stored.distanceKm()).isEqualTo(new BigDecimal(km));
    assertThat(stored.durationSec()).isEqualTo(1800);
    assertThat(stored.weightKg()).isNull();
    assertThat(stored.reps()).isNull();
  }

  @Test
  void 距離のないセットと回数のみのセットも保存の形にする() {
    assertThat(SetRules.toStored(new SetInput(null, null, dec("60"), null), "kg", "km"))
        .isEqualTo(new StoredSet(null, null, 60, null));
    assertThat(SetRules.toStored(new SetInput(null, dec("20.0"), null, null), "kg", "km"))
        .isEqualTo(new StoredSet(null, 20, null, null));
  }

  private static Map<String, String> check(
      SetInput set, String recordType, String weightUnit, String distanceUnit) {
    return SetRules.check(set, recordType, weightUnit, distanceUnit);
  }

  private static Map<String, String> check(SetInput set, String recordType) {
    return SetRules.check(set, recordType, "kg", "km");
  }

  private static SetInput weightReps(String weight, String reps) {
    return new SetInput(dec(weight), dec(reps), null, null);
  }

  private static SetInput timeDistance(String distance) {
    return new SetInput(null, null, dec("1800"), dec(distance));
  }

  private static BigDecimal dec(String value) {
    return new BigDecimal(value);
  }
}
