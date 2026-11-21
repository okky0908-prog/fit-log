package com.okimoto.fitlog.exercise.calc;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 種目の決まり（docs/database.md「exercises」、docs/test-plan.md 5.1・5.10） */
class ExerciseRulesTest {

  @ParameterizedTest(name = "[{index}] {0} → {1}")
  @CsvSource(
      delimiter = '|',
      value = {
        // 半角カナは全角カナに（濁点も1文字にまとまる）
        "ﾍﾞﾝﾁﾌﾟﾚｽ | ベンチプレス",
        // 全角英数字は半角に、英字は小文字に
        "ＤＢカール２ | dbカール2",
        "Leg Press | leg press",
        // 前後の空白（全角の空白も）は除く
        "'　ランジ ' | ランジ",
      })
  void 名前のキーは_NFKCで正規化して小文字にする(String name, String key) {
    assertThat(ExerciseRules.nameKey(name)).isEqualTo(key);
  }

  @Test
  void 検索の文字は_キーと同じに正規化し_LIKEの特別な文字はそのままの文字として扱う() {
    assertThat(ExerciseRules.containsPattern("ＢＥＮＣＨ")).isEqualTo("%bench%");
    assertThat(ExerciseRules.containsPattern("100%_\\")).isEqualTo("%100\\%\\_\\\\%");
  }

  @ParameterizedTest(name = "[{index}] {0}・{1} → {2}")
  @CsvSource({
    "chest, weight_reps, 3.5",
    "cardio, weight_reps, 3.5",
    "back, reps, 3.8",
    "cardio, reps, 3.8",
    "cardio, time_distance, 6.0",
    "legs, time_distance, 3.5",
  })
  void 自分専用の種目のMETsは_記録方式と部位で決まる(String bodyPart, String recordType, String mets) {
    assertThat(ExerciseRules.defaultMets(bodyPart, recordType)).isEqualTo(new BigDecimal(mets));
  }
}
