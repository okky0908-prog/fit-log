package com.okimoto.fitlog.like.service;

import com.okimoto.fitlog.common.controller.ApiException;
import com.okimoto.fitlog.common.controller.ErrorCode;
import com.okimoto.fitlog.like.repository.LikeRepository;
import com.okimoto.fitlog.notification.repository.NotificationRepository;
import com.okimoto.fitlog.notification.service.NotificationEvent;
import com.okimoto.fitlog.workout.repository.WorkoutRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ナイストレーニング（F-40〜F-42、docs/api.md A-63・A-64）。付けるのも取り消すのも、何回呼んでも結果が同じ。
 *
 * <p>付けたら投稿者に like の通知を作り（自分の記録なら作らない）、取り消したらその通知を消す。見てはいけない記録は、ない記録と同じ 404
 */
@Service
public class LikeService {

  /** ナイストレーニングの通知の種類 */
  static final String LIKE = "like";

  private final LikeRepository likeRepository;
  private final WorkoutRepository workoutRepository;
  private final NotificationRepository notificationRepository;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public LikeService(
      LikeRepository likeRepository,
      WorkoutRepository workoutRepository,
      NotificationRepository notificationRepository,
      ApplicationEventPublisher events,
      Clock clock) {
    this.likeRepository = likeRepository;
    this.workoutRepository = workoutRepository;
    this.notificationRepository = notificationRepository;
    this.events = events;
    this.clock = clock;
  }

  /**
   * 操作のあとの、ナイストレーニングの数と自分が付けたか。
   *
   * @param likeCount 数
   * @param likedByMe 自分が付けたか
   */
  public record LikeState(int likeCount, boolean likedByMe) {}

  /** A-63 付ける。すでに付いていれば、今の状態を返す（通知も作らない） */
  @Transactional
  public LikeState like(long me, long workoutId) {
    long author = findVisibleAuthor(me, workoutId);
    if (likeRepository.insertIfAbsent(workoutId, me, OffsetDateTime.now(clock))) {
      events.publishEvent(new NotificationEvent(LIKE, author, me, workoutId));
    }
    return new LikeState(likeRepository.count(workoutId), true);
  }

  /** A-64 取り消す。付いていなくてもエラーにしない */
  @Transactional
  public LikeState unlike(long me, long workoutId) {
    findVisibleAuthor(me, workoutId);
    if (likeRepository.delete(workoutId, me)) {
      notificationRepository.deleteFromActorOnWorkout(me, workoutId, LIKE);
    }
    return new LikeState(likeRepository.count(workoutId), false);
  }

  private long findVisibleAuthor(long me, long workoutId) {
    return workoutRepository
        .lockVisibleForShare(workoutId, me)
        .orElseThrow(() -> new ApiException(ErrorCode.WORKOUT_NOT_FOUND));
  }
}
