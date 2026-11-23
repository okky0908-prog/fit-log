package com.okimoto.fitlog.user.service;

import com.okimoto.fitlog.common.controller.ApiException;
import com.okimoto.fitlog.common.controller.ErrorCode;
import com.okimoto.fitlog.follow.calc.FollowRules;
import com.okimoto.fitlog.follow.repository.FollowRepository;
import com.okimoto.fitlog.jooq.tables.records.UsersRecord;
import com.okimoto.fitlog.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** プロフィール（F-60、docs/api.md A-80） */
@Service
public class ProfileService {

  private final UserRepository userRepository;
  private final FollowRepository followRepository;

  public ProfileService(UserRepository userRepository, FollowRepository followRepository) {
    this.userRepository = userRepository;
    this.followRepository = followRepository;
  }

  /**
   * プロフィール。鍵アカウントでも、名前・自己紹介・数は返す（F-50）。
   *
   * @param username ユーザー名
   * @param displayName 表示名
   * @param bio 自己紹介
   * @param isPrivate 鍵アカウントか
   * @param followingCount フォロー中の数（承認済みだけ）
   * @param followerCount フォロワーの数（承認済みだけ）
   * @param me 自分自身か
   * @param followStatus 自分から相手へのフォローの状態（none・requested・following）
   * @param canViewRecords 記録・公開した目標・フォロー一覧を見られるか
   */
  public record Profile(
      String username,
      String displayName,
      String bio,
      boolean isPrivate,
      int followingCount,
      int followerCount,
      boolean me,
      String followStatus,
      boolean canViewRecords) {}

  /** A-80 プロフィール。ユーザー名は大文字・小文字を区別しない。いなければ 404 */
  @Transactional(readOnly = true)
  public Profile profile(long me, String username) {
    UsersRecord user =
        userRepository
            .findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    boolean self = user.getId() == me;
    String myStatus = self ? null : followRepository.findStatus(me, user.getId()).orElse(null);
    return new Profile(
        user.getUsername(),
        user.getDisplayName(),
        user.getBio(),
        user.getIsPrivate(),
        followRepository.countFollowing(user.getId()),
        followRepository.countFollowers(user.getId()),
        self,
        FollowRules.followStatus(myStatus),
        FollowRules.canViewRecords(self, user.getIsPrivate(), myStatus));
  }
}
