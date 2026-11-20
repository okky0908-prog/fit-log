package com.okimoto.fitlog.common.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * エラーの種類（docs/api.md「エラー」）。レスポンスの {@code code} にこの名前がそのまま入る。
 *
 * <p>画面は文言（message）ではなく code でエラーを見分ける。種類を増やすときは、ここと docs/api.md の表に同じ名前を足す。
 * 一度使い始めた名前は変えない。使う機能を作るときに、表の種類をここに足していく
 */
public enum ErrorCode {

  // ===== 400 =====
  /** 入力チェックのエラー。項目ごとの内容は errors に入る */
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "入力内容に誤りがあります"),
  /** JSON が壊れているなど、リクエストを読めない */
  MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "リクエストの形式が正しくありません"),
  /** 確認用のリンクのトークンが、期限切れ（期限ちょうども含む）・使用済み・不正 */
  INVALID_TOKEN(HttpStatus.BAD_REQUEST, "このリンクは使えません"),

  // ===== 401 =====
  /** アクセストークンがない・無効・期限切れ。画面は A-04 で再発行して1回だけやり直す */
  UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "ログインが必要です"),
  /** ログインで、メールアドレスかパスワードが違う（どちらが違うかは教えない） */
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "メールアドレスまたはパスワードが正しくありません"),
  /** リフレッシュトークンがない・無効・期限切れ。もう一度ログインしてもらう */
  SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "ログインの有効期限が切れました。もう一度ログインしてください"),

  // ===== 403 =====
  /** 見られるが、操作する権限がない */
  FORBIDDEN(HttpStatus.FORBIDDEN, "この操作は実行できません"),

  // ===== 404 / 405 =====
  /** 存在しない URL */
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "指定された URL は存在しません"),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "この URL では、この操作はできません"),

  // ===== 409 / 413 / 415 =====
  /** ユーザー名・メールアドレスが登録済み。どの項目かは errors に入る */
  ALREADY_REGISTERED(HttpStatus.CONFLICT, "すでに登録されています"),
  /** 確認済みのメールアドレスに、確認メールを再送しようとした */
  ALREADY_VERIFIED(HttpStatus.CONFLICT, "メールアドレスの確認はすでに済んでいます"),
  PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "送信するデータが大きすぎます"),
  UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "リクエストの形式（Content-Type）に対応していません"),

  // ===== 429 =====
  /** 同じメールアドレスでログインの失敗が続いたので、しばらくログインを受け付けない（総当たりの対策） */
  TOO_MANY_LOGIN_ATTEMPTS(
      HttpStatus.TOO_MANY_REQUESTS, "ログインの失敗が続いたため、しばらくログインできません。15分ほど待ってからもう一度お試しください"),
  /** 同じアドレスへのメールが1時間で5通を超える */
  TOO_MANY_EMAILS(HttpStatus.TOO_MANY_REQUESTS, "メールの送信が続いたため、しばらく送れません。1時間ほど待ってからもう一度お試しください"),

  // ===== 500 / 503 =====
  /** 想定していないエラー。内部の情報は利用者に出さず、ログにだけ残す */
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "サーバーでエラーが発生しました。時間をおいてもう一度お試しください"),
  /** DB につながらないなど */
  SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "ただいまサービスを利用できません。時間をおいてもう一度お試しください"),
  /** ログイン・新規登録が集中して、パスワードの照合の順番を待ちきれなかった（ほかの API の CPU を残すため） */
  SERVER_BUSY(HttpStatus.SERVICE_UNAVAILABLE, "ただいま混み合っています。少し待ってからもう一度お試しください");

  private final HttpStatus status;
  private final String message;

  ErrorCode(HttpStatus status, String message) {
    this.status = status;
    this.message = message;
  }

  public HttpStatus status() {
    return status;
  }

  /** 利用者に見せる文言 */
  public String message() {
    return message;
  }

  /** Spring が決めたステータス（404・405 など）に合う種類。個別の種類がないものは、4xx なら MALFORMED_REQUEST、それ以外は INTERNAL_ERROR */
  public static ErrorCode fromStatus(HttpStatusCode status) {
    return switch (status.value()) {
      case 401 -> UNAUTHENTICATED;
      case 403 -> FORBIDDEN;
      case 404 -> RESOURCE_NOT_FOUND;
      case 405 -> METHOD_NOT_ALLOWED;
      case 413 -> PAYLOAD_TOO_LARGE;
      case 415 -> UNSUPPORTED_MEDIA_TYPE;
      case 503 -> SERVICE_UNAVAILABLE;
      default -> status.is4xxClientError() ? MALFORMED_REQUEST : INTERNAL_ERROR;
    };
  }
}
