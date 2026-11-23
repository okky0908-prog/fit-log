package com.okimoto.fitlog.notification.controller;

import com.okimoto.fitlog.auth.config.AuthenticatedUser;
import com.okimoto.fitlog.notification.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 通知の API（docs/api.md A-120〜A-122） */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

  private final NotificationService notificationService;

  public NotificationController(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  /**
   * 一覧の1ページ（docs/api.md「ページング」）。
   *
   * @param items 通知
   * @param nextCursor 続きのカーソル。続きがなければ null
   * @param hasNext 続きがあるか
   */
  public record PageResponse(
      List<NotificationResponse> items, String nextCursor, boolean hasNext) {}

  /**
   * 未読の数（A-121）。
   *
   * @param unreadCount 未読の通知の数（100 で数えるのをやめる）
   * @param followRequestCount 自分へのフォローのリクエストの数（フォローを作るまで 0）
   */
  public record UnreadCountResponse(int unreadCount, int followRequestCount) {}

  /**
   * 既読にする通知（A-122）。
   *
   * @param ids 画面に表示した通知の ID（50件まで）
   */
  public record ReadRequest(
      @NotNull(message = "既読にする通知を指定してください") @Size(max = 50, message = "既読にする通知は50件まで指定できます")
          List<Long> ids) {}

  /** A-120 通知一覧 */
  @GetMapping
  public PageResponse list(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) String cursor) {
    NotificationService.Page page = notificationService.list(user.id(), cursor);
    return new PageResponse(
        page.items().stream().map(NotificationResponse::from).toList(),
        page.nextCursor(),
        page.nextCursor() != null);
  }

  /** A-121 未読の数 */
  @GetMapping("/unread-count")
  public UnreadCountResponse unreadCount(@AuthenticationPrincipal AuthenticatedUser user) {
    return new UnreadCountResponse(notificationService.unreadCount(user.id()), 0);
  }

  /** A-122 既読にする */
  @PostMapping("/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void read(
      @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody ReadRequest request) {
    notificationService.markRead(user.id(), request.ids());
  }
}
