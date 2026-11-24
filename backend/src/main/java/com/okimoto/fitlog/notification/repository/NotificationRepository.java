package com.okimoto.fitlog.notification.repository;

import static com.okimoto.fitlog.jooq.Tables.NOTIFICATIONS;
import static com.okimoto.fitlog.jooq.Tables.USERS;
import static com.okimoto.fitlog.jooq.Tables.WORKOUTS;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.row;
import static org.jooq.impl.DSL.select;

import com.okimoto.fitlog.common.calc.Cursor;
import com.okimoto.fitlog.jooq.tables.Users;
import com.okimoto.fitlog.workout.repository.WorkoutVisibility;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Records;
import org.jooq.SelectJoinStep;
import org.springframework.stereotype.Repository;

/**
 * 通知（notifications）の読み書き（docs/database.md「notifications」）。
 *
 * <p>見られなくなった投稿についての通知は、行を消さずに一覧と未読数から除く（公開範囲を広げ直したら、また見えるようにするため）
 */
@Repository
public class NotificationRepository {

  /** 操作した人（投稿者の users と分けるための別名） */
  private static final Users ACTOR = USERS.as("actor");

  private final DSLContext dsl;

  public NotificationRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * 通知を保存する。自分への通知・種類に合わない列の値は、DB の CHECK で止まる。
   *
   * @return 保存した通知の ID
   */
  public long insert(NewNotification notification) {
    return dsl.insertInto(NOTIFICATIONS)
        .set(NOTIFICATIONS.RECIPIENT_ID, notification.recipientId())
        .set(NOTIFICATIONS.ACTOR_ID, notification.actorId())
        .set(NOTIFICATIONS.TYPE, notification.type())
        .set(NOTIFICATIONS.WORKOUT_ID, notification.workoutId())
        .set(NOTIFICATIONS.CREATED_AT, notification.createdAt())
        .returning(NOTIFICATIONS.ID)
        .fetchSingle()
        .getId();
  }

  /**
   * 通知一覧（A-120）。新しい順（同じ日時なら ID の大きい順）に、件数まで。
   *
   * @param after カーソルの位置。この位置より古いものを返す。最初のページは null
   */
  public List<NotificationRow> findPage(long recipientId, Cursor.Position after, int limit) {
    Condition afterCursor =
        after == null
            ? noCondition()
            : row(NOTIFICATIONS.CREATED_AT, NOTIFICATIONS.ID).lt(after.createdAt(), after.id());
    return dsl.select(
            NOTIFICATIONS.ID,
            NOTIFICATIONS.TYPE,
            NOTIFICATIONS.CREATED_AT,
            NOTIFICATIONS.READ_AT,
            ACTOR.USERNAME,
            ACTOR.DISPLAY_NAME,
            ACTOR.IS_PRIVATE,
            ACTOR.ICON_KEY,
            WORKOUTS.ID,
            WORKOUTS.BODY)
        .from(NOTIFICATIONS)
        .leftJoin(ACTOR)
        .on(ACTOR.ID.eq(NOTIFICATIONS.ACTOR_ID))
        .leftJoin(WORKOUTS)
        .on(WORKOUTS.ID.eq(NOTIFICATIONS.WORKOUT_ID))
        .leftJoin(USERS)
        .on(USERS.ID.eq(WORKOUTS.USER_ID))
        .where(NOTIFICATIONS.RECIPIENT_ID.eq(recipientId))
        .and(visibleTo(recipientId))
        .and(afterCursor)
        .orderBy(NOTIFICATIONS.CREATED_AT.desc(), NOTIFICATIONS.ID.desc())
        .limit(limit)
        .fetch(Records.mapping(NotificationRow::new));
  }

  /** 未読の数（A-121）。一覧に出る通知のうち未読のもの。cap 件で数えるのをやめる（画面は「99+」） */
  public int countUnread(long recipientId, int cap) {
    return dsl.fetchCount(
        withWorkouts(select(NOTIFICATIONS.ID))
            .where(NOTIFICATIONS.RECIPIENT_ID.eq(recipientId))
            .and(NOTIFICATIONS.READ_AT.isNull())
            .and(visibleTo(recipientId))
            .limit(cap));
  }

  /**
   * 既読にする（A-122）。自分の未読の通知だけ。ほかの人の通知・既読の通知・ない ID は何もしない
   *
   * @return 既読にした数
   */
  public int markRead(long recipientId, Collection<Long> ids, OffsetDateTime now) {
    return dsl.update(NOTIFICATIONS)
        .set(NOTIFICATIONS.READ_AT, now)
        .where(NOTIFICATIONS.RECIPIENT_ID.eq(recipientId))
        .and(NOTIFICATIONS.ID.in(ids))
        .and(NOTIFICATIONS.READ_AT.isNull())
        .execute();
  }

  /**
   * 取り消し（フォローの解除・リクエストの取り消しなど）で、その操作の通知を消す。
   *
   * @return 消した数
   */
  public int deleteFromActor(long recipientId, long actorId, String type) {
    return dsl.deleteFrom(NOTIFICATIONS)
        .where(NOTIFICATIONS.RECIPIENT_ID.eq(recipientId))
        .and(NOTIFICATIONS.ACTOR_ID.eq(actorId))
        .and(NOTIFICATIONS.TYPE.eq(type))
        .execute();
  }

  /**
   * 記録への操作の取り消し（ナイストレーニングなど）で、その人からのその記録への通知を消す。
   *
   * @return 消した数
   */
  public int deleteFromActorOnWorkout(long actorId, long workoutId, String type) {
    return dsl.deleteFrom(NOTIFICATIONS)
        .where(NOTIFICATIONS.ACTOR_ID.eq(actorId))
        .and(NOTIFICATIONS.WORKOUT_ID.eq(workoutId))
        .and(NOTIFICATIONS.TYPE.eq(type))
        .execute();
  }

  private static SelectJoinStep<?> withWorkouts(org.jooq.SelectSelectStep<?> select) {
    return select
        .from(NOTIFICATIONS)
        .leftJoin(WORKOUTS)
        .on(WORKOUTS.ID.eq(NOTIFICATIONS.WORKOUT_ID))
        .leftJoin(USERS)
        .on(USERS.ID.eq(WORKOUTS.USER_ID));
  }

  /** 投稿についての通知なら、受け取る人がその投稿を見てよいか（{@link WorkoutVisibility}）。投稿についての通知でなければ見てよい */
  private static Condition visibleTo(long recipientId) {
    return NOTIFICATIONS.WORKOUT_ID.isNull().or(WorkoutVisibility.visibleTo(recipientId));
  }
}
