package com.okimoto.fitlog.like.repository;

import static com.okimoto.fitlog.jooq.Tables.LIKES;

import java.time.OffsetDateTime;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/** ナイストレーニング（likes）の読み書き */
@Repository
public class LikeRepository {

  private final DSLContext dsl;

  public LikeRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * ナイストレーニングを付ける。すでに付いていれば何もしない（同時に2回来ても1件。一意制約と ON CONFLICT DO NOTHING）。
   *
   * @return 新しく付けたか
   */
  public boolean insertIfAbsent(long workoutId, long userId, OffsetDateTime now) {
    return dsl.insertInto(LIKES)
            .set(LIKES.WORKOUT_ID, workoutId)
            .set(LIKES.USER_ID, userId)
            .set(LIKES.CREATED_AT, now)
            .onConflictDoNothing()
            .execute()
        == 1;
  }

  /**
   * ナイストレーニングを取り消す。
   *
   * @return 付いていたものを消したか
   */
  public boolean delete(long workoutId, long userId) {
    return dsl.deleteFrom(LIKES)
            .where(LIKES.WORKOUT_ID.eq(workoutId))
            .and(LIKES.USER_ID.eq(userId))
            .execute()
        == 1;
  }

  /**
   * まだ付いているか。通知を作る直前に確かめる。行をロック（FOR SHARE）して、通知を作り終えるまで取り消しを待たせる （docs/test-plan.md
   * 4.5。NotificationGuard の説明）
   */
  public boolean lockExists(long workoutId, long userId) {
    return dsl.selectOne()
        .from(LIKES)
        .where(LIKES.WORKOUT_ID.eq(workoutId))
        .and(LIKES.USER_ID.eq(userId))
        .forShare()
        .fetchOptional()
        .isPresent();
  }

  /** その記録のナイストレーニングの数 */
  public int count(long workoutId) {
    return dsl.fetchCount(LIKES, LIKES.WORKOUT_ID.eq(workoutId));
  }
}
