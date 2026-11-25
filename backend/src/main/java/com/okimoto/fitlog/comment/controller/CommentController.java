package com.okimoto.fitlog.comment.controller;

import com.okimoto.fitlog.auth.config.AuthenticatedUser;
import com.okimoto.fitlog.comment.controller.CommentResponses.CommentResponse;
import com.okimoto.fitlog.comment.controller.CommentResponses.Item;
import com.okimoto.fitlog.comment.controller.CommentResponses.ReplyResponse;
import com.okimoto.fitlog.comment.service.CommentService;
import com.okimoto.fitlog.image.service.ImageUrls;
import java.util.List;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** コメント・返信の API（docs/api.md A-60〜A-62・A-65） */
@RestController
public class CommentController {

  private final CommentService commentService;
  private final ImageUrls imageUrls;

  public CommentController(CommentService commentService, ImageUrls imageUrls) {
    this.commentService = commentService;
    this.imageUrls = imageUrls;
  }

  /**
   * コメント・返信の投稿のリクエスト。
   *
   * @param body 本文
   * @param replyToCommentId 返信するコメント・返信。コメントなら null
   */
  public record CommentRequest(String body, Long replyToCommentId) {}

  /**
   * 投稿の結果。
   *
   * @param comment 書いたコメント（返信なら返信の形）
   * @param commentCount 投稿後のコメント数（返信を含む）
   */
  public record PostedResponse(Item comment, int commentCount) {}

  /**
   * 削除の結果。
   *
   * @param commentCount 削除後のコメント数（返信を含む）
   */
  public record CountResponse(int commentCount) {}

  /**
   * 一覧の1ページ（docs/api.md「ページング」）。
   *
   * @param items 一覧
   * @param nextCursor 続きのカーソル。続きがなければ null
   * @param hasNext 続きがあるか
   */
  public record PageResponse<T>(List<T> items, String nextCursor, boolean hasNext) {

    static <R, T> PageResponse<T> from(CommentService.Page<R> page, Function<R, T> mapper) {
      return new PageResponse<>(
          page.items().stream().map(mapper).toList(), page.nextCursor(), page.nextCursor() != null);
    }
  }

  /** A-60 コメント一覧 */
  @GetMapping("/api/workouts/{workoutId}/comments")
  public PageResponse<CommentResponse> list(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable long workoutId,
      @RequestParam(required = false) String cursor) {
    return PageResponse.from(
        commentService.list(user.id(), workoutId, cursor),
        row -> CommentResponse.from(row, imageUrls::iconUrlOf));
  }

  /** A-61 コメント・返信の投稿 */
  @PostMapping("/api/workouts/{workoutId}/comments")
  @ResponseStatus(HttpStatus.CREATED)
  public PostedResponse post(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable long workoutId,
      @RequestBody CommentRequest request) {
    CommentService.Posted posted =
        commentService.post(user.id(), workoutId, request.body(), request.replyToCommentId());
    Item comment =
        posted.reply()
            ? ReplyResponse.from(posted.comment(), imageUrls::iconUrlOf)
            : CommentResponse.posted(posted.comment(), imageUrls::iconUrlOf);
    return new PostedResponse(comment, posted.commentCount());
  }

  /** A-62 コメントの削除 */
  @DeleteMapping("/api/comments/{commentId}")
  public CountResponse delete(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable long commentId) {
    return new CountResponse(commentService.delete(user.id(), commentId));
  }

  /** A-65 返信の続き */
  @GetMapping("/api/comments/{commentId}/replies")
  public PageResponse<ReplyResponse> replies(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable long commentId,
      @RequestParam(required = false) String cursor) {
    return PageResponse.from(
        commentService.replies(user.id(), commentId, cursor),
        row -> ReplyResponse.from(row, imageUrls::iconUrlOf));
  }
}
