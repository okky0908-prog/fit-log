package com.okimoto.fitlog.workout.calc;

/** 記録の決まり（docs/features.md「記録の項目」） */
public final class WorkoutRules {

  /** ひとことの最大の文字数（コードポイントで数える） */
  public static final int BODY_MAX_LENGTH = 280;

  private WorkoutRules() {}

  /** ひとことを、保存する形にそろえる。改行を LF にそろえ、前後の空白・改行を除く。なければ空 */
  public static String normalizeBody(String body) {
    return body == null ? "" : body.replace("\r\n", "\n").strip();
  }

  /** そろえたあとのひとことが長すぎるか */
  public static boolean bodyTooLong(String normalizedBody) {
    return normalizedBody.codePointCount(0, normalizedBody.length()) > BODY_MAX_LENGTH;
  }
}
