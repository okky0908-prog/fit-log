package com.okimoto.fitlog.like.service;

import com.okimoto.fitlog.like.repository.LikeRepository;
import com.okimoto.fitlog.notification.service.NotificationEvent;
import com.okimoto.fitlog.notification.service.NotificationGuard;
import org.springframework.stereotype.Component;

/** ナイストレーニング（like）の通知が、まだ正しいか。操作した人のナイストレーニングがその記録に残っていれば正しい */
@Component
public class LikeNotificationGuard implements NotificationGuard {

  private final LikeRepository likeRepository;

  public LikeNotificationGuard(LikeRepository likeRepository) {
    this.likeRepository = likeRepository;
  }

  @Override
  public boolean supports(String type) {
    return LikeService.LIKE.equals(type);
  }

  @Override
  public boolean isStillValid(NotificationEvent event) {
    return likeRepository.lockExists(event.workoutId(), event.actorId());
  }
}
