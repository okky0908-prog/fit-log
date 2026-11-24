package com.okimoto.fitlog.like.controller;

import com.okimoto.fitlog.auth.config.AuthenticatedUser;
import com.okimoto.fitlog.like.service.LikeService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ナイストレーニングの API（docs/api.md A-63・A-64） */
@RestController
@RequestMapping("/api/workouts/{workoutId}/likes")
public class LikeController {

  private final LikeService likeService;

  public LikeController(LikeService likeService) {
    this.likeService = likeService;
  }

  /**
   * 操作のあとの、ナイストレーニングの数と自分が付けたか。
   *
   * @param likeCount 数
   * @param likedByMe 自分が付けたか
   */
  public record LikeResponse(int likeCount, boolean likedByMe) {

    static LikeResponse from(LikeService.LikeState state) {
      return new LikeResponse(state.likeCount(), state.likedByMe());
    }
  }

  /** A-63 付ける */
  @PostMapping
  public LikeResponse like(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable long workoutId) {
    return LikeResponse.from(likeService.like(user.id(), workoutId));
  }

  /** A-64 取り消す */
  @DeleteMapping
  public LikeResponse unlike(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable long workoutId) {
    return LikeResponse.from(likeService.unlike(user.id(), workoutId));
  }
}
