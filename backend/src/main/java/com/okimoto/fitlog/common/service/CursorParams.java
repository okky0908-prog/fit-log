package com.okimoto.fitlog.common.service;

import com.okimoto.fitlog.common.calc.Cursor;
import com.okimoto.fitlog.common.controller.ApiError.FieldError;
import com.okimoto.fitlog.common.controller.ApiException;
import com.okimoto.fitlog.common.controller.ErrorCode;
import java.util.List;

/** 一覧の API で受け取ったカーソルを読む。形が違えば 400（VALIDATION_FAILED、field は cursor。docs/api.md「ページング」） */
public final class CursorParams {

  static final String MESSAGE = "一覧の続きを読み込めません。最初から読み込み直してください";

  private CursorParams() {}

  /** 日時と ID のカーソル。最初のページ（cursor なし）は null */
  public static Cursor.Position position(String cursor) {
    return cursor == null ? null : Cursor.decode(cursor).orElseThrow(CursorParams::invalid);
  }

  /** 実施日つきのカーソル。最初のページ（cursor なし）は null */
  public static Cursor.DatedPosition datedPosition(String cursor) {
    return cursor == null ? null : Cursor.decodeDated(cursor).orElseThrow(CursorParams::invalid);
  }

  private static ApiException invalid() {
    return new ApiException(
        ErrorCode.VALIDATION_FAILED, List.of(new FieldError("cursor", MESSAGE)));
  }
}
