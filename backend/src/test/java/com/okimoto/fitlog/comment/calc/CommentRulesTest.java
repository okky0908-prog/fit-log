package com.okimoto.fitlog.comment.calc;

import static org.assertj.core.api.Assertions.assertThat;

import com.okimoto.fitlog.comment.calc.CommentRules.Notice;
import com.okimoto.fitlog.common.controller.ApiError.FieldError;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** コメントの本文（docs/test-plan.md 5.1：1〜140文字）と、通知を作るか（5.9 のデシジョンテーブル） */
class CommentRulesTest {

  @Nested
  class 本文 {

    @Test
    void 前後の空白と改行を除き_改行をそろえる() {
      assertThat(CommentRules.body("  \r\nフォーム\r\nきれい \n")).isEqualTo("フォーム\nきれい");
      assertThat(CommentRules.body(null)).isNull();
    }

    @Test
    void 整えたあとの文字数で_1文字と140文字は正しい_絵文字も1文字() {
      assertThat(CommentRules.problem("あ")).isEmpty();
      assertThat(CommentRules.problem("あ".repeat(139) + "💪")).isEmpty();
      assertThat(CommentRules.problem("  " + "あ".repeat(140) + "\n")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\r\n"})
    void 空は誤り(String body) {
      assertThat(CommentRules.problem(body))
          .contains(new FieldError("body", CommentRules.BODY_MESSAGE));
    }

    @Test
    void 文字数が141と_送られていないものは誤り() {
      assertThat(CommentRules.problem("あ".repeat(141))).isPresent();
      assertThat(CommentRules.problem(null)).isPresent();
    }
  }

  /** 操作した人 1・投稿者 2・ほかの人 3 */
  @Nested
  class 通知を作るか {

    @Test
    void コメントは投稿者にcomment() {
      assertThat(CommentRules.notices(1, 2, null))
          .containsExactly(new Notice(CommentRules.COMMENT, 2));
    }

    @Test
    void 自分の記録へのコメントは作らない() {
      assertThat(CommentRules.notices(1, 1, null)).isEmpty();
    }

    @Test
    void 自分のコメントへの返信は_投稿者にcommentだけ() {
      assertThat(CommentRules.notices(1, 2, 1L))
          .containsExactly(new Notice(CommentRules.COMMENT, 2));
    }

    @Test
    void 自分の記録の自分のコメントへの返信は作らない() {
      assertThat(CommentRules.notices(1, 1, 1L)).isEmpty();
    }

    @Test
    void 投稿者のコメントへの返信は_投稿者にreplyを1件だけ() {
      assertThat(CommentRules.notices(1, 2, 2L)).containsExactly(new Notice(CommentRules.REPLY, 2));
    }

    @Test
    void ほかの人への返信は_返信先にreply_投稿者にcomment() {
      assertThat(CommentRules.notices(1, 2, 3L))
          .containsExactly(new Notice(CommentRules.REPLY, 3), new Notice(CommentRules.COMMENT, 2));
    }

    @Test
    void 自分の記録でほかの人へ返信したら_返信先にreplyだけ() {
      assertThat(CommentRules.notices(1, 1, 3L)).containsExactly(new Notice(CommentRules.REPLY, 3));
    }
  }
}
