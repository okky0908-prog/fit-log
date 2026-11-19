package com.okimoto.fitlog.common.controller;

import java.util.List;

/**
 * エラーのレスポンスの形（docs/api.md「エラー」）。
 *
 * @param status HTTP ステータスと同じ値
 * @param code エラーの種類（{@link ErrorCode} の名前）。画面はこれで見分ける
 * @param message 利用者にそのまま見せてよい文言
 * @param errors 項目ごとの入力エラー。なければ空
 */
public record ApiError(int status, String code, String message, List<FieldError> errors) {

  /**
   * 項目ごとの入力エラー。
   *
   * @param field 項目の場所（入れ子は {@code exercises[0].sets[2].weight} の形）
   * @param message その欄の下に出す文言
   */
  public record FieldError(String field, String message) {}

  public static ApiError of(ErrorCode code) {
    return of(code, List.of());
  }

  public static ApiError of(ErrorCode code, List<FieldError> errors) {
    return new ApiError(code.status().value(), code.name(), code.message(), List.copyOf(errors));
  }
}
