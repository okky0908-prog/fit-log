package com.okimoto.fitlog.follow.controller;

import com.okimoto.fitlog.auth.config.AuthenticatedUser;
import com.okimoto.fitlog.follow.calc.FollowRules;
import com.okimoto.fitlog.follow.repository.FollowUserRow;
import com.okimoto.fitlog.follow.service.FollowService;
import com.okimoto.fitlog.image.service.ImageUrls;
import java.util.List;
import java.util.function.Function;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** フォローの API（docs/api.md A-70〜A-73） */
@RestController
@RequestMapping("/api/users/{username}")
public class FollowController {

  private final FollowService followService;
  private final ImageUrls imageUrls;

  public FollowController(FollowService followService, ImageUrls imageUrls) {
    this.followService = followService;
    this.imageUrls = imageUrls;
  }

  /**
   * フォロー・解除の結果。
   *
   * @param followStatus 自分から相手へのフォローの状態（none・requested・following）
   */
  public record FollowStatusResponse(String followStatus) {}

  /**
   * フォロー一覧の1人（UserSummary に、自己紹介・自分から見た状態・自分自身かを足したもの）。
   *
   * @param username ユーザー名
   * @param displayName 表示名
   * @param iconUrl アイコンの署名付き URL。なければ null
   * @param isPrivate 鍵アカウントか
   * @param bio 自己紹介
   * @param followStatus 自分からこの人へのフォローの状態（none・requested・following）
   * @param me 自分自身か
   */
  public record UserListItem(
      String username,
      String displayName,
      String iconUrl,
      boolean isPrivate,
      String bio,
      String followStatus,
      boolean me) {

    static UserListItem from(FollowUserRow row, Function<String, String> iconUrlOf) {
      return new UserListItem(
          row.username(),
          row.displayName(),
          iconUrlOf.apply(row.iconKey()),
          row.isPrivate(),
          row.bio(),
          FollowRules.followStatus(row.myStatus()),
          row.me());
    }
  }

  /**
   * 一覧の1ページ（docs/api.md「ページング」）。
   *
   * @param items 一覧の人
   * @param nextCursor 続きのカーソル。続きがなければ null
   * @param hasNext 続きがあるか
   */
  public record PageResponse(List<UserListItem> items, String nextCursor, boolean hasNext) {

    static PageResponse from(FollowService.Page page, Function<String, String> iconUrlOf) {
      return new PageResponse(
          page.items().stream().map(row -> UserListItem.from(row, iconUrlOf)).toList(),
          page.nextCursor(),
          page.nextCursor() != null);
    }
  }

  /** A-70 フォロー（鍵アカウントならリクエスト） */
  @PostMapping("/follow")
  public FollowStatusResponse follow(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable String username) {
    return new FollowStatusResponse(followService.follow(user.id(), username));
  }

  /** A-71 フォロー解除・リクエストの取り消し */
  @DeleteMapping("/follow")
  public FollowStatusResponse unfollow(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable String username) {
    return new FollowStatusResponse(followService.unfollow(user.id(), username));
  }

  /** A-72 フォロー中一覧 */
  @GetMapping("/following")
  public PageResponse following(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable String username,
      @RequestParam(required = false) String cursor) {
    return PageResponse.from(
        followService.following(user.id(), username, cursor), imageUrls::iconUrlOf);
  }

  /** A-73 フォロワー一覧 */
  @GetMapping("/followers")
  public PageResponse followers(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable String username,
      @RequestParam(required = false) String cursor) {
    return PageResponse.from(
        followService.followers(user.id(), username, cursor), imageUrls::iconUrlOf);
  }
}
