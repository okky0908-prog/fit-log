package com.okimoto.fitlog.user.controller;

import com.okimoto.fitlog.auth.config.AuthenticatedUser;
import com.okimoto.fitlog.user.service.ProfileService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ユーザーの API（docs/api.md A-80） */
@RestController
@RequestMapping("/api/users")
public class UserController {

  private final ProfileService profileService;

  public UserController(ProfileService profileService) {
    this.profileService = profileService;
  }

  /**
   * プロフィール（A-80）。
   *
   * @param username ユーザー名
   * @param displayName 表示名
   * @param iconUrl アイコンの URL（アイコンの保存を作るまでは null）
   * @param bio 自己紹介
   * @param isPrivate 鍵アカウントか
   * @param followingCount フォロー中の数
   * @param followerCount フォロワーの数
   * @param me 自分自身か
   * @param followStatus 自分から相手へのフォローの状態（none・requested・following）
   * @param canViewRecords 記録・公開した目標・フォロー一覧を見られるか
   */
  public record ProfileResponse(
      String username,
      String displayName,
      String iconUrl,
      String bio,
      boolean isPrivate,
      int followingCount,
      int followerCount,
      boolean me,
      String followStatus,
      boolean canViewRecords) {

    static ProfileResponse from(ProfileService.Profile profile) {
      return new ProfileResponse(
          profile.username(),
          profile.displayName(),
          null,
          profile.bio(),
          profile.isPrivate(),
          profile.followingCount(),
          profile.followerCount(),
          profile.me(),
          profile.followStatus(),
          profile.canViewRecords());
    }
  }

  /** A-80 プロフィール */
  @GetMapping("/{username}")
  public ProfileResponse profile(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable String username) {
    return ProfileResponse.from(profileService.profile(user.id(), username));
  }
}
