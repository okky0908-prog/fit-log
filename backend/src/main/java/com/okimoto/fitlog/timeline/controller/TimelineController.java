package com.okimoto.fitlog.timeline.controller;

import com.okimoto.fitlog.auth.config.AuthenticatedUser;
import com.okimoto.fitlog.image.service.ImageUrls;
import com.okimoto.fitlog.timeline.service.TimelineService;
import com.okimoto.fitlog.workout.controller.WorkoutCardResponse;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** タイムライン（A-50〜A-53）と、そのユーザーの記録一覧（A-81） */
@RestController
public class TimelineController {

  private final TimelineService timelineService;
  private final ImageUrls imageUrls;

  public TimelineController(TimelineService timelineService, ImageUrls imageUrls) {
    this.timelineService = timelineService;
    this.imageUrls = imageUrls;
  }

  /**
   * 記録の一覧の1ページ（docs/api.md「ページング」）。
   *
   * @param items 一覧の記録
   * @param nextCursor 続きのカーソル。続きがなければ null
   * @param hasNext 続きがあるか
   */
  public record PageResponse(List<WorkoutCardResponse> items, String nextCursor, boolean hasNext) {}

  /**
   * 新しい投稿の件数。
   *
   * @param count 件数（100件で数えるのをやめる）
   */
  public record NewCountResponse(int count) {}

  /** A-50 フォロー中タイムライン */
  @GetMapping("/api/timeline")
  public PageResponse following(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) String cursor) {
    return response(timelineService.timeline(user.id(), true, cursor), user.id());
  }

  /** A-51 全体タイムライン */
  @GetMapping("/api/timeline/all")
  public PageResponse all(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) String cursor) {
    return response(timelineService.timeline(user.id(), false, cursor), user.id());
  }

  /** A-52 フォロー中タイムラインの新しい投稿の件数。since は画面が最後に取った1ページ目の一番新しい記録の ID */
  @GetMapping("/api/timeline/new-count")
  public NewCountResponse followingNewCount(
      @AuthenticationPrincipal AuthenticatedUser user, @RequestParam long since) {
    return new NewCountResponse(timelineService.newCount(user.id(), true, since));
  }

  /** A-53 全体タイムラインの新しい投稿の件数 */
  @GetMapping("/api/timeline/all/new-count")
  public NewCountResponse allNewCount(
      @AuthenticationPrincipal AuthenticatedUser user, @RequestParam long since) {
    return new NewCountResponse(timelineService.newCount(user.id(), false, since));
  }

  /** A-81 そのユーザーの記録一覧（実施日の新しい順） */
  @GetMapping("/api/users/{username}/workouts")
  public PageResponse userWorkouts(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable String username,
      @RequestParam(required = false) String cursor) {
    return response(timelineService.userWorkouts(user.id(), username, cursor), user.id());
  }

  private PageResponse response(TimelineService.Page page, long me) {
    return new PageResponse(
        page.items().stream()
            .map(row ->
                WorkoutCardResponse.from(
                    row, page.personalBests(), me, imageUrls::urlOf, imageUrls::iconUrlOf))
            .toList(),
        page.nextCursor(),
        page.nextCursor() != null);
  }
}
