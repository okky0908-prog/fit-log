package com.okimoto.fitlog.common.calc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 本文の抜き出し（docs/api.md「Notification」の bodyExcerpt：先頭の30文字。超えたら「…」を付ける） */
class ExcerptTest {

  @Test
  void 三十文字までは_そのまま() {
    assertThat(Excerpt.of("あ".repeat(30), 30)).isEqualTo("あ".repeat(30));
    assertThat(Excerpt.of("", 30)).isEmpty();
  }

  @Test
  void 三十文字を超えたら_三十文字に切って三点リーダーを付ける() {
    assertThat(Excerpt.of("あ".repeat(31), 30)).isEqualTo("あ".repeat(30) + "…");
  }

  @Test
  void 絵文字も1文字と数え_途中で切らない() {
    assertThat(Excerpt.of("💪".repeat(31), 30)).isEqualTo("💪".repeat(30) + "…");
  }

  @Test
  void 改行は空白にして_1行にする() {
    assertThat(Excerpt.of("脚の日\n#脚トレ", 30)).isEqualTo("脚の日 #脚トレ");
  }
}
