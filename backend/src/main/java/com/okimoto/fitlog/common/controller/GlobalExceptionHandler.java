package com.okimoto.fitlog.common.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Controller とその先で起きた例外を、docs/api.md のエラーの形にする。
 *
 * <ul>
 *   <li>業務上のエラー（{@link ApiException}）と入力チェックのエラーは、決まった code で返す
 *   <li>存在しない URL・使えないメソッドなど、Spring がステータスを決めるエラーは、そのステータスに合う code で返す
 *   <li>それ以外は 500 にし、内部の情報は返さずにログにだけ残す
 * </ul>
 *
 * <p>Controller より手前（Security のフィルター）で起きたエラーは、SecurityConfig と {@link ApiErrorController} が同じ形にする
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiError> handleApiException(ApiException e) {
    return respond(e.toApiError());
  }

  /** {@code @Valid} の入力チェックのエラー。項目ごとの文言を errors に入れる */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
    // 1つの項目が、同じ文言の決まりに2つ違反したとき（空のメールアドレスは「空でない」と「形式」の両方）、同じエラーを2つ返さない
    List<ApiError.FieldError> errors =
        e.getBindingResult().getFieldErrors().stream()
            .map(error -> new ApiError.FieldError(error.getField(), error.getDefaultMessage()))
            .distinct()
            .toList();
    return respond(ApiError.of(ErrorCode.VALIDATION_FAILED, errors));
  }

  /** JSON が壊れているなど、リクエストを読めない */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException e) {
    return respond(ApiError.of(ErrorCode.MALFORMED_REQUEST));
  }

  /**
   * ここまでのどれにも当たらない例外。Spring がステータスを決めている 4xx（404・405・415 など）はそのステータスで返し、それ以外は想定していないエラーとして 500
   * にしてログに残す
   */
  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> handleOthers(Exception e, HttpServletRequest request) {
    if (e instanceof ErrorResponse errorResponse) {
      ErrorCode code = ErrorCode.fromStatus(errorResponse.getStatusCode());
      if (code.status().is4xxClientError()) {
        return respond(ApiError.of(code));
      }
    }
    log.error("想定していないエラー: {} {}", request.getMethod(), request.getRequestURI(), e);
    return respond(ApiError.of(ErrorCode.INTERNAL_ERROR));
  }

  private static ResponseEntity<ApiError> respond(ApiError body) {
    return ResponseEntity.status(body.status()).body(body);
  }
}
