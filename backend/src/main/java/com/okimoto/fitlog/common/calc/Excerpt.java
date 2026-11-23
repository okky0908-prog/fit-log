package com.okimoto.fitlog.common.calc;

/** 本文の抜き出し（通知に出す、投稿・コメントの先頭。docs/api.md「Notification」の bodyExcerpt） */
public final class Excerpt {

  private static final String ELLIPSIS = "…";

  private Excerpt() {}

  /** 改行を空白にして1行にし、先頭の maxLength 文字にする（超えたら「…」を付ける）。絵文字などは途中で切らないよう、コードポイントで数える */
  public static String of(String text, int maxLength) {
    String line = text.replaceAll("\\R", " ");
    if (line.codePointCount(0, line.length()) <= maxLength) {
      return line;
    }
    return line.substring(0, line.offsetByCodePoints(0, maxLength)) + ELLIPSIS;
  }
}
