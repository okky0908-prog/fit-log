package com.okimoto.fitlog.workout.calc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ひとことの整え方と長さ（docs/api.md A-20、docs/test-plan.md 5.1） */
class WorkoutRulesTest {

  @Test
  void ひとことは前後の空白と改行を除き_改行をLFにそろえる() {
    assertThat(WorkoutRules.normalizeBody(" \r\n脚の日\r\n#脚トレ\n　")).isEqualTo("脚の日\n#脚トレ");
  }

  @Test
  void ひとことがなければ空にする() {
    assertThat(WorkoutRules.normalizeBody(null)).isEmpty();
    assertThat(WorkoutRules.normalizeBody(" \n ")).isEmpty();
  }

  @Test
  void ひとことは280文字まで_絵文字も1文字_改行も1文字() {
    assertThat(WorkoutRules.bodyTooLong("あ".repeat(280))).isFalse();
    assertThat(WorkoutRules.bodyTooLong("あ".repeat(281))).isTrue();
    assertThat(WorkoutRules.bodyTooLong("💪".repeat(280))).isFalse();
    // \r\n を \n にそろえたあとで数えるので、279文字と改行で280文字
    assertThat(
            WorkoutRules.bodyTooLong(
                WorkoutRules.normalizeBody("あ".repeat(139) + "\r\n" + "あ".repeat(140))))
        .isFalse();
  }
}
