package com.okimoto.fitlog.user.service;

import com.okimoto.fitlog.common.controller.ApiError.FieldError;
import com.okimoto.fitlog.common.controller.ApiException;
import com.okimoto.fitlog.common.controller.ErrorCode;
import com.okimoto.fitlog.follow.calc.FollowRules;
import com.okimoto.fitlog.follow.repository.FollowRepository;
import com.okimoto.fitlog.image.calc.ImageRules;
import com.okimoto.fitlog.image.calc.ImageType;
import com.okimoto.fitlog.image.service.ImageStorage;
import com.okimoto.fitlog.jooq.tables.records.UsersRecord;
import com.okimoto.fitlog.user.calc.ProfileRules;
import com.okimoto.fitlog.user.repository.UserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** プロフィール（F-60・F-61、docs/api.md A-80・A-82） */
@Service
public class ProfileService {

  private final UserRepository userRepository;
  private final FollowRepository followRepository;
  private final ImageStorage imageStorage;
  private final Clock clock;

  public ProfileService(
      UserRepository userRepository,
      FollowRepository followRepository,
      ImageStorage imageStorage,
      Clock clock) {
    this.userRepository = userRepository;
    this.followRepository = followRepository;
    this.imageStorage = imageStorage;
    this.clock = clock;
  }

  /**
   * プロフィール。鍵アカウントでも、名前・自己紹介・数は返す（F-50）。
   *
   * @param username ユーザー名
   * @param displayName 表示名
   * @param iconKey アイコンの S3 のキー。なければ null
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
      String iconKey,
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
    return profileOf(me, user);
  }

  /**
   * A-82 プロフィールの編集。入力とアイコンの誤りは、まとめて 400 で返す（何も変えない）。
   *
   * <p>アイコンは送ったときだけ差し替える。新しいアイコンは DB より先に S3 に保存し、保存が取り消されたら消す。古いアイコンは保存の確定後に消す
   *
   * @param icon アイコンの中身。送らなければ null
   */
  @Transactional
  public Profile update(long me, ProfileInput input, byte[] icon) {
    List<FieldError> errors =
        new ArrayList<>(ProfileRules.problems(input.displayName(), input.bio()));
    if (icon != null) {
      ImageRules.problem(icon).ifPresent(message -> errors.add(new FieldError("icon", message)));
    }
    if (!errors.isEmpty()) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, errors);
    }
    // 同時に2回差し替えたときに、古いアイコンを消し漏らさないよう、行をロックしてから今のアイコンを読む
    userRepository.lockById(me);
    String oldIconKey = userRepository.findById(me).orElseThrow().getIconKey();
    String newIconKey =
        icon == null ? null : imageStorage.storeIcon(icon, ImageType.detect(icon).orElseThrow());
    userRepository.updateProfile(
        me,
        ProfileRules.displayName(input.displayName()),
        ProfileRules.bio(input.bio()),
        newIconKey,
        OffsetDateTime.now(clock));
    if (newIconKey != null && oldIconKey != null) {
      imageStorage.discardAfterCommit(oldIconKey);
    }
    return profileOf(me, userRepository.findById(me).orElseThrow());
  }

  private Profile profileOf(long me, UsersRecord user) {
    boolean self = user.getId() == me;
    String myStatus = self ? null : followRepository.findStatus(me, user.getId()).orElse(null);
    return new Profile(
        user.getUsername(),
        user.getDisplayName(),
        user.getIconKey(),
        user.getBio(),
        user.getIsPrivate(),
        followRepository.countFollowing(user.getId()),
        followRepository.countFollowers(user.getId()),
        self,
        FollowRules.followStatus(myStatus),
        FollowRules.canViewRecords(self, user.getIsPrivate(), myStatus));
  }
}
