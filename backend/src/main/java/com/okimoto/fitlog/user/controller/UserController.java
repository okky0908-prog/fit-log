package com.okimoto.fitlog.user.controller;

import com.okimoto.fitlog.auth.config.AuthenticatedUser;
import com.okimoto.fitlog.image.service.ImageUrls;
import com.okimoto.fitlog.user.service.ProfileInput;
import com.okimoto.fitlog.user.service.ProfileService;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** ユーザーの API（docs/api.md A-80・A-82） */
@RestController
@RequestMapping("/api/users")
public class UserController {

  private final ProfileService profileService;
  private final ImageUrls imageUrls;

  public UserController(ProfileService profileService, ImageUrls imageUrls) {
    this.profileService = profileService;
    this.imageUrls = imageUrls;
  }

  /**
   * プロフィール（A-80）。
   *
   * @param username ユーザー名
   * @param displayName 表示名
   * @param iconUrl アイコンの署名付き URL。なければ null
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

    static ProfileResponse from(ProfileService.Profile profile, ImageUrls imageUrls) {
      return new ProfileResponse(
          profile.username(),
          profile.displayName(),
          imageUrls.iconUrlOf(profile.iconKey()),
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
    return ProfileResponse.from(profileService.profile(user.id(), username), imageUrls);
  }

  /**
   * A-82 プロフィールの編集。アイコンを一緒に送るので multipart/form-data で受け取り、表示名・自己紹介の JSON は {@code data} のパート、アイコンは
   * {@code icon} のパート（送ったときだけ差し替える）に入れる。A-80 と同じ形で、更新後のプロフィールを返す
   */
  @PutMapping(path = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ProfileResponse update(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestPart("data") ProfileInput data,
      @RequestPart(name = "icon", required = false) MultipartFile icon)
      throws IOException {
    byte[] content = icon == null ? null : icon.getBytes();
    return ProfileResponse.from(profileService.update(user.id(), data, content), imageUrls);
  }
}
