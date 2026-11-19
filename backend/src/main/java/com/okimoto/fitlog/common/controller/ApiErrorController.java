package com.okimoto.fitlog.common.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller より手前（フィルターなど）で起きたエラーを、docs/api.md のエラーの形で返す。
 *
 * <p>Spring Boot の標準（timestamp・error・path の形）を置き換える。Controller の中で起きたエラーは {@link
 * GlobalExceptionHandler} が先に処理するので、ここには来ない
 */
@RestController
public class ApiErrorController implements ErrorController {

  private static final Logger log = LoggerFactory.getLogger(ApiErrorController.class);

  @RequestMapping("/error")
  ResponseEntity<ApiError> error(HttpServletRequest request) {
    // エラーの転送ではなく /error を直接開かれたときは、ステータスがないので 404 にする
    ErrorCode code =
        request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer status
            ? ErrorCode.fromStatus(HttpStatusCode.valueOf(status))
            : ErrorCode.RESOURCE_NOT_FOUND;
    if (code.status().is5xxServerError()) {
      log.error(
          "想定していないエラー: {}",
          request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI),
          (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION));
    }
    ApiError body = ApiError.of(code);
    return ResponseEntity.status(body.status()).body(body);
  }
}
