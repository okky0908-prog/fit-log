package com.okimoto.fitlog.user.calc;

import static org.assertj.core.api.Assertions.assertThat;

import com.okimoto.fitlog.common.controller.ApiError.FieldError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** プロフィールの編集の入力（docs/api.md A-82、docs/test-plan.md 5.1 の表示名・自己紹介） */
class ProfileRulesTest {

  @Test
  void 表示名は前後の空白を除く() {
    assertThat(ProfileRules.displayName("  山田太郎　\n")).isEqualTo("山田太郎");
  }

  @Test
  void 自己紹介は改行をそろえて前後の空白を除き_空ならnull() {
    assertThat(ProfileRules.bio("\r\n 週4で\r\n筋トレ \r\n")).isEqualTo("週4で\n筋トレ");
    assertThat(ProfileRules.bio(" \r\n ")).isNull();
    assertThat(ProfileRules.bio(null)).isNull();
  }

  @ParameterizedTest(name = "表示名 {0} 文字 → 誤り {1}")
  @CsvSource({"0, true", "1, false", "50, false", "51, true"})
  void 表示名は1から50文字(int length, boolean error) {
    assertThat(ProfileRules.problems("あ".repeat(length), null).isEmpty()).isEqualTo(!error);
  }

  @Test
  void 表示名がなければ誤り_空白だけも誤り() {
    assertThat(ProfileRules.problems(null, null))
        .containsExactly(new FieldError("displayName", ProfileRules.DISPLAY_NAME_MESSAGE));
    assertThat(ProfileRules.problems("　 ", null)).hasSize(1);
  }

  @Test
  void 絵文字も1文字と数える() {
    assertThat(ProfileRules.problems("💪".repeat(50), "🏃".repeat(160))).isEmpty();
  }

  @ParameterizedTest(name = "自己紹介 {0} 文字 → 誤り {1}")
  @CsvSource({"0, false", "160, false", "161, true"})
  void 自己紹介は160文字まで(int length, boolean error) {
    assertThat(ProfileRules.problems("山田", "あ".repeat(length)))
        .isEqualTo(
            error
                ? java.util.List.of(new FieldError("bio", ProfileRules.BIO_MESSAGE))
                : java.util.List.of());
  }

  @Test
  void 改行はそろえてから数える_CRLFを2文字と数えない() {
    assertThat(ProfileRules.problems("山田", "あ\r\n".repeat(80))).isEmpty();
  }

  @Test
  void 両方が誤りなら_両方を返す() {
    assertThat(ProfileRules.problems("", "あ".repeat(161)))
        .extracting(FieldError::field)
        .containsExactly("displayName", "bio");
  }
}
