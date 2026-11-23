package com.okimoto.fitlog.user.calc;

import com.okimoto.fitlog.common.controller.ApiError.FieldError;
import java.util.ArrayList;
import java.util.List;

/** プロフィールの編集の入力の整え方とチェック（docs/api.md A-82） */
public final class ProfileRules {

  public static final String DISPLAY_NAME_MESSAGE = "表示名は1〜50文字で入力してください";
  public static final String BIO_MESSAGE = "自己紹介は160文字までで入力してください";

  private ProfileRules() {}

  /** 表示名の文字数の範囲 */
  static final int DISPLAY_NAME_MAX = 50;

  /** 自己紹介の文字数の上限 */
  static final int BIO_MAX = 160;

  /** 保存する表示名。前後の空白・改行を除く。null は null のまま */
  public static String displayName(String value) {
    return value == null ? null : value.strip();
  }

  /** 保存する自己紹介。改行を \n にそろえ（ブラウザはフォームで \r\n を送る）、前後の空白・改行を除く。空なら null */
  public static String bio(String value) {
    if (value == null) {
      return null;
    }
    String text = value.replace("\r\n", "\n").strip();
    return text.isEmpty() ? null : text;
  }

  /**
   * 入力の誤り。整えたあとの文字数を、コードポイントで数える（絵文字も1文字。画面の {@code [...text].length} と同じ）。
   *
   * @return 項目ごとの誤り（表示名 → 自己紹介の順）。なければ空
   */
  public static List<FieldError> problems(String displayName, String bio) {
    List<FieldError> errors = new ArrayList<>();
    int nameLength = length(displayName(displayName));
    if (nameLength < 1 || nameLength > DISPLAY_NAME_MAX) {
      errors.add(new FieldError("displayName", DISPLAY_NAME_MESSAGE));
    }
    if (length(bio(bio)) > BIO_MAX) {
      errors.add(new FieldError("bio", BIO_MESSAGE));
    }
    return List.copyOf(errors);
  }

  private static int length(String text) {
    return text == null ? 0 : text.codePointCount(0, text.length());
  }
}
