package com.okimoto.fitlog.common.controller;

import java.util.List;

/** 業務上のエラー。{@link GlobalExceptionHandler} が、code と errors をそのままエラーのレスポンスにする */
public class ApiException extends RuntimeException {

  private final transient ApiError error;

  public ApiException(ErrorCode code, List<ApiError.FieldError> errors) {
    super(code.name());
    this.error = ApiError.of(code, errors);
  }

  public ApiException(ErrorCode code) {
    this(code, List.of());
  }

  public ApiError toApiError() {
    return error;
  }
}
