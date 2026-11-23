package com.okimoto.fitlog.follow.service;

import com.okimoto.fitlog.follow.calc.FollowRules;
import com.okimoto.fitlog.follow.repository.FollowRepository;
import com.okimoto.fitlog.notification.service.NotificationEvent;
import com.okimoto.fitlog.notification.service.NotificationGuard;
import org.springframework.stereotype.Component;

/**
 * フォロー（follow）・フォローのリクエスト（follow_request）の通知が、まだ正しいかを確かめる。
 *
 * <p>操作した人から受け取る人へのフォローが、通知の種類に合う状態（フォローなら承認済み、リクエストなら承認待ち）で残っていれば正しい。 作る前に解除・取り消しされていれば作らない
 */
@Component
public class FollowNotificationGuard implements NotificationGuard {

  private final FollowRepository followRepository;

  public FollowNotificationGuard(FollowRepository followRepository) {
    this.followRepository = followRepository;
  }

  @Override
  public boolean supports(String type) {
    return FollowService.FOLLOW.equals(type) || FollowService.FOLLOW_REQUEST.equals(type);
  }

  @Override
  public boolean isStillValid(NotificationEvent event) {
    String expected =
        FollowService.FOLLOW.equals(event.type()) ? FollowRules.ACCEPTED : FollowRules.PENDING;
    return followRepository
        .lockStatus(event.actorId(), event.recipientId())
        .map(expected::equals)
        .orElse(false);
  }
}
