package com.okimoto.fitlog.body.calc;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 体重の記録の入力チェックと kg への換算（docs/features.md「体重の記録」、docs/test-plan.md 5.x の境界値） */
class BodyRecordRulesTest {

  @Nested
  class 体重_kg {

    @ParameterizedTest
    @ValueSource(strings = {"20.0", "20", "73.0", "73.1", "300.0"})
    void 二十から三百までの0点1刻みは通る(String weight) {
      assertThat(BodyRecordRules.check(new BigDecimal(weight), "kg", null)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"19.9", "300.1", "73.05", "0", "-73.0"})
    void 範囲の外と刻みに合わない値は通らない(String weight) {
      assertThat(BodyRecordRules.check(new BigDecimal(weight), "kg", null))
          .containsExactly(Map.entry("weight", BodyRecordRules.KG_WEIGHT));
    }
  }

  @Nested
  class 体重_lb {

    @ParameterizedTest
    @ValueSource(strings = {"44.0", "150.2", "661.0"})
    void 四十四から六百六十一までの0点2刻みは通る(String weight) {
      assertThat(BodyRecordRules.check(new BigDecimal(weight), "lb", null)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"43.8", "661.2", "150.1"})
    void 範囲の外と刻みに合わない値は通らない(String weight) {
      assertThat(BodyRecordRules.check(new BigDecimal(weight), "lb", null))
          .containsExactly(Map.entry("weight", BodyRecordRules.LB_WEIGHT));
    }
  }

  @Nested
  class 体脂肪率 {

    @ParameterizedTest
    @ValueSource(strings = {"1.0", "18.5", "75.0"})
    void 一から七十五までの0点1刻みは通る(String pct) {
      assertThat(BodyRecordRules.check(new BigDecimal("73.0"), "kg", new BigDecimal(pct)))
          .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.9", "75.1", "18.55"})
    void 範囲の外と刻みに合わない値は通らない(String pct) {
      assertThat(BodyRecordRules.check(new BigDecimal("73.0"), "kg", new BigDecimal(pct)))
          .containsExactly(Map.entry("bodyFatPct", BodyRecordRules.BODY_FAT));
    }
  }

  @Test
  void 体重がない_単位が正しくないときのエラー_すべての項目のエラーを集める() {
    assertThat(BodyRecordRules.check(null, "kg", null))
        .containsExactly(Map.entry("weight", BodyRecordRules.KG_WEIGHT));
    assertThat(BodyRecordRules.check(null, "lb", null))
        .containsExactly(Map.entry("weight", BodyRecordRules.LB_WEIGHT));
    // 単位が正しくなければ、体重の範囲は確かめない
    assertThat(BodyRecordRules.check(new BigDecimal("73.0"), "st", new BigDecimal("0.5")))
        .containsExactly(
            Map.entry("weightUnit", BodyRecordRules.UNIT),
            Map.entry("bodyFatPct", BodyRecordRules.BODY_FAT));
    assertThat(BodyRecordRules.check(new BigDecimal("73.0"), null, null))
        .containsExactly(Map.entry("weightUnit", BodyRecordRules.UNIT));
  }

  @Test
  void kgにして小数第3位に四捨五入する() {
    assertThat(BodyRecordRules.toKg(new BigDecimal("73.0"), "kg"))
        .isEqualTo(new BigDecimal("73.000"));
    // 44.0 × 0.45359237 ＝ 19.9580…（kg の下限 20.0 を下回るが、DB の下限 19.95 の内側）
    assertThat(BodyRecordRules.toKg(new BigDecimal("44.0"), "lb"))
        .isEqualTo(new BigDecimal("19.958"));
    // 661.0 × 0.45359237 ＝ 299.8245…
    assertThat(BodyRecordRules.toKg(new BigDecimal("661.0"), "lb"))
        .isEqualTo(new BigDecimal("299.825"));
  }

  @Test
  void 推移の期間の始まりの日は_3か月前と1年前_それ以外は空() {
    LocalDate today = LocalDate.parse("2026-05-31");
    assertThat(BodyRecordRules.trendFrom("3m", today)).contains(LocalDate.parse("2026-02-28"));
    assertThat(BodyRecordRules.trendFrom("1y", today)).contains(LocalDate.parse("2025-05-31"));
    assertThat(BodyRecordRules.trendFrom("all", today)).isEmpty();
    assertThat(BodyRecordRules.trendFrom("3M", today)).isEmpty();
  }
}
