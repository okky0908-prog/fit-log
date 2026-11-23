package com.okimoto.fitlog.workout.repository;

import static com.okimoto.fitlog.jooq.Tables.FOLLOWS;
import static com.okimoto.fitlog.jooq.Tables.USERS;
import static com.okimoto.fitlog.jooq.Tables.WORKOUTS;
import static org.jooq.impl.DSL.exists;
import static org.jooq.impl.DSL.selectOne;

import org.jooq.Condition;

/**
 * 「ログイン中のユーザーが、その記録を見てよいか」の条件（docs/database.md「閲覧の制御の条件」）。記録を読むすべての SQL で、この条件を使う。
 *
 * <p>使う SQL は、workouts と、投稿者の users（{@code users.id = workouts.user_id}）を結合しておく
 */
public final class WorkoutVisibility {

  private WorkoutVisibility() {}

  public static Condition visibleTo(long me) {
    return WORKOUTS
        .USER_ID
        .eq(me) // 本人はすべて見られる
        .or(
            WORKOUTS
                .VISIBILITY
                .ne("private") // 自分のみの記録は、本人以外は見られない
                .and(
                    // 鍵アカウントでない人の公開の記録は、全員が見られる
                    WORKOUTS
                        .VISIBILITY
                        .eq("public")
                        .and(USERS.IS_PRIVATE.isFalse())
                        // それ以外（フォロワー限定・鍵アカウントの公開）は、承認済みのフォロワーだけ
                        .or(isAcceptedFollower(me))));
  }

  /** me が、投稿者の承認済みのフォロワーか（リクエスト中は含めない。向きを取り違えない：me がフォローする側） */
  private static Condition isAcceptedFollower(long me) {
    return exists(
        selectOne()
            .from(FOLLOWS)
            .where(FOLLOWS.FOLLOWER_ID.eq(me))
            .and(FOLLOWS.FOLLOWEE_ID.eq(WORKOUTS.USER_ID))
            .and(FOLLOWS.STATUS.eq("accepted")));
  }
}
