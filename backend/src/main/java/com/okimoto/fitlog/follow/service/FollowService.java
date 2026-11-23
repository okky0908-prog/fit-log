package com.okimoto.fitlog.follow.service;

import com.okimoto.fitlog.common.calc.Cursor;
import com.okimoto.fitlog.common.controller.ApiError.FieldError;
import com.okimoto.fitlog.common.controller.ApiException;
import com.okimoto.fitlog.common.controller.ErrorCode;
import com.okimoto.fitlog.follow.calc.FollowRules;
import com.okimoto.fitlog.follow.repository.FollowRepository;
import com.okimoto.fitlog.follow.repository.FollowUserRow;
import com.okimoto.fitlog.jooq.tables.records.UsersRecord;
import com.okimoto.fitlog.notification.repository.NotificationRepository;
import com.okimoto.fitlog.notification.service.NotificationEvent;
import com.okimoto.fitlog.user.repository.UserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** フォロー・フォロー解除・フォロー一覧（F-50〜F-52・F-57、docs/api.md A-70〜A-73） */
@Service
public class FollowService {

  /** フォローの通知の種類 */
  static final String FOLLOW = "follow";

  /** フォローのリクエストの通知の種類 */
  static final String FOLLOW_REQUEST = "follow_request";

  /** 一覧の1ページの件数 */
  static final int PAGE_SIZE = 20;

  private final FollowRepository followRepository;
  private final UserRepository userRepository;
  private final NotificationRepository notificationRepository;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public FollowService(
      FollowRepository followRepository,
      UserRepository userRepository,
      NotificationRepository notificationRepository,
      ApplicationEventPublisher events,
      Clock clock) {
    this.followRepository = followRepository;
    this.userRepository = userRepository;
    this.notificationRepository = notificationRepository;
    this.events = events;
    this.clock = clock;
  }

  /**
   * 一覧の1ページと、続きのカーソル。
   *
   * @param items 一覧の人（フォローした日時の新しい順）
   * @param nextCursor 続きのカーソル。続きがなければ null
   */
  public record Page(List<FollowUserRow> items, String nextCursor) {}

  /**
   * A-70 フォロー。相手が鍵アカウントならリクエストにする。すでにフォロー中・リクエスト中なら、今の状態を返す（通知も作らない）。
   *
   * @return 自分から相手へのフォローの状態（following・requested）
   */
  @Transactional
  public String follow(long me, String username) {
    UsersRecord target = findOther(me, username);
    String status = target.getIsPrivate() ? FollowRules.PENDING : FollowRules.ACCEPTED;
    if (followRepository.insertIfAbsent(me, target.getId(), status, OffsetDateTime.now(clock))) {
      String type = FollowRules.ACCEPTED.equals(status) ? FOLLOW : FOLLOW_REQUEST;
      events.publishEvent(new NotificationEvent(type, target.getId(), me, null));
      return FollowRules.followStatus(status);
    }
    // 同時に来たもう一方のリクエストが作った行を含め、今の状態を返す
    return FollowRules.followStatus(followRepository.findStatus(me, target.getId()).orElse(null));
  }

  /**
   * A-71 フォロー解除・リクエストの取り消し。その通知も消す。フォローしていなくてもエラーにしない。
   *
   * @return none
   */
  @Transactional
  public String unfollow(long me, String username) {
    UsersRecord target = findOther(me, username);
    followRepository
        .delete(me, target.getId())
        .ifPresent(status ->
            notificationRepository.deleteFromActor(
                target.getId(), me, FollowRules.ACCEPTED.equals(status) ? FOLLOW : FOLLOW_REQUEST));
    return FollowRules.NONE;
  }

  /** A-72 フォロー中一覧 */
  @Transactional(readOnly = true)
  public Page following(long me, String username, String cursor) {
    long owner = findViewable(me, username);
    Cursor.Position after = decode(cursor);
    return page(followRepository.findFollowing(owner, me, after, PAGE_SIZE + 1));
  }

  /** A-73 フォロワー一覧 */
  @Transactional(readOnly = true)
  public Page followers(long me, String username, String cursor) {
    long owner = findViewable(me, username);
    Cursor.Position after = decode(cursor);
    return page(followRepository.findFollowers(owner, me, after, PAGE_SIZE + 1));
  }

  /** 自分以外のユーザー。いなければ 404、自分なら 400 */
  private UsersRecord findOther(long me, String username) {
    UsersRecord target = findUser(username);
    if (target.getId() == me) {
      throw new ApiException(ErrorCode.CANNOT_FOLLOW_SELF);
    }
    return target;
  }

  /** 一覧を見てよいユーザーの ID。いなければ 404、鍵アカウントで見てはいけなければ 403 */
  private long findViewable(long me, String username) {
    UsersRecord owner = findUser(username);
    boolean self = owner.getId() == me;
    String myStatus = self ? null : followRepository.findStatus(me, owner.getId()).orElse(null);
    if (!FollowRules.canViewRecords(self, owner.getIsPrivate(), myStatus)) {
      throw new ApiException(ErrorCode.PRIVATE_ACCOUNT);
    }
    return owner.getId();
  }

  private UsersRecord findUser(String username) {
    return userRepository
        .findByUsernameIgnoreCase(username)
        .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
  }

  private static Cursor.Position decode(String cursor) {
    if (cursor == null) {
      return null;
    }
    return Cursor.decode(cursor)
        .orElseThrow(() ->
            new ApiException(
                ErrorCode.VALIDATION_FAILED,
                List.of(new FieldError("cursor", "一覧の続きを読み込めません。最初から読み込み直してください"))));
  }

  /** 1件多く取った結果から、1ページと続きのカーソルを作る */
  private static Page page(List<FollowUserRow> rows) {
    if (rows.size() <= PAGE_SIZE) {
      return new Page(rows, null);
    }
    List<FollowUserRow> items = rows.subList(0, PAGE_SIZE);
    FollowUserRow last = items.getLast();
    return new Page(items, Cursor.encode(last.acceptedAt(), last.id()));
  }
}
