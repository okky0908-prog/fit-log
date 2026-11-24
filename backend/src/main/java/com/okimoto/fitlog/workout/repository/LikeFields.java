package com.okimoto.fitlog.workout.repository;

import static com.okimoto.fitlog.jooq.Tables.LIKES;
import static com.okimoto.fitlog.jooq.Tables.WORKOUTS;
import static org.jooq.impl.DSL.exists;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.selectCount;
import static org.jooq.impl.DSL.selectOne;

import org.jooq.Field;

/**
 * 記録を読む SQL に足す、ナイストレーニングの数と「自分が付けたか」の列。同じ SQL の中のサブクエリにして、一覧でも1件ごとに SQL を投げない（docs/database.md「N+1
 * 問題を防ぐ」）。使う SQL は workouts を読んでおく
 */
final class LikeFields {

  private LikeFields() {}

  static Field<Integer> likeCount() {
    return field(selectCount().from(LIKES).where(LIKES.WORKOUT_ID.eq(WORKOUTS.ID)));
  }

  static Field<Boolean> likedBy(long me) {
    return field(
        exists(
            selectOne()
                .from(LIKES)
                .where(LIKES.WORKOUT_ID.eq(WORKOUTS.ID))
                .and(LIKES.USER_ID.eq(me))));
  }
}
