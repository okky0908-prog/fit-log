package com.okimoto.fitlog.workout.repository;

import static com.okimoto.fitlog.jooq.Tables.USERS;
import static com.okimoto.fitlog.jooq.Tables.WORKOUTS;

import org.jooq.Condition;

/**
 * 「ログイン中のユーザーが、その記録を見てよいか」の条件（docs/database.md「閲覧の制御の条件」）。記録を読むすべての SQL で、この条件を使う。
 *
 * <p>使う SQL は、workouts と、投稿者の users（{@code users.id = workouts.user_id}）を結合しておく。
 *
 * <p>フォロワー限定の記録と、鍵アカウントの公開の記録を承認済みのフォロワーが見られる条件は、フォローの表を作るときに足す（それまでは本人だけが見られる）
 */
public final class WorkoutVisibility {

  private WorkoutVisibility() {}

  public static Condition visibleTo(long me) {
    return WORKOUTS
        .USER_ID
        .eq(me)
        .or(WORKOUTS.VISIBILITY.eq("public").and(USERS.IS_PRIVATE.isFalse()));
  }
}
