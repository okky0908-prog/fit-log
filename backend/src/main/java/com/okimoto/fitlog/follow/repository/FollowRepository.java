package com.okimoto.fitlog.follow.repository;

import static com.okimoto.fitlog.jooq.Tables.FOLLOWS;
import static com.okimoto.fitlog.jooq.Tables.USERS;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.row;

import com.okimoto.fitlog.common.calc.Cursor;
import com.okimoto.fitlog.follow.calc.FollowRules;
import com.okimoto.fitlog.jooq.tables.Follows;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Records;
import org.jooq.TableField;
import org.springframework.stereotype.Repository;

/**
 * フォロー・フォローのリクエスト（follows）の読み書き（docs/database.md「follows」）。
 *
 * <p>数・一覧・閲覧の判定は、承認済み（accepted）の行だけを使う
 */
@Repository
public class FollowRepository {

  private static final String ACCEPTED = FollowRules.ACCEPTED;
  private static final String PENDING = FollowRules.PENDING;

  /** 見ている人から、一覧の各人へのフォロー（一覧の元のフォローと分けるための別名） */
  private static final Follows MINE = FOLLOWS.as("mine");

  private final DSLContext dsl;

  public FollowRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * フォロー・リクエストを作る。すでにあれば何もしない（同時に2回来ても1件。UNIQUE と ON CONFLICT）。
   *
   * @param status accepted（承認済み）か pending（リクエスト中）
   * @return 作ったか
   */
  public boolean insertIfAbsent(long follower, long followee, String status, OffsetDateTime now) {
    return dsl.insertInto(FOLLOWS)
            .set(FOLLOWS.FOLLOWER_ID, follower)
            .set(FOLLOWS.FOLLOWEE_ID, followee)
            .set(FOLLOWS.STATUS, status)
            .set(FOLLOWS.CREATED_AT, now)
            .set(FOLLOWS.ACCEPTED_AT, ACCEPTED.equals(status) ? now : null)
            .onConflictDoNothing()
            .execute()
        == 1;
  }

  /** follower から followee へのフォローの状態（accepted・pending）。なければ空 */
  public Optional<String> findStatus(long follower, long followee) {
    return dsl.select(FOLLOWS.STATUS)
        .from(FOLLOWS)
        .where(FOLLOWS.FOLLOWER_ID.eq(follower))
        .and(FOLLOWS.FOLLOWEE_ID.eq(followee))
        .fetchOptional(FOLLOWS.STATUS);
  }

  /**
   * follower から followee へのフォローの状態を、行をロック（FOR SHARE）して読む。通知を作る直前の確かめに使う。
   *
   * <p>通知を作り終えるまで、解除（行の削除）を待たせる。解除が先なら行はないので、通知を作らない。どちらの順でも、解除のあとに通知が残らない
   */
  public Optional<String> lockStatus(long follower, long followee) {
    return dsl.select(FOLLOWS.STATUS)
        .from(FOLLOWS)
        .where(FOLLOWS.FOLLOWER_ID.eq(follower))
        .and(FOLLOWS.FOLLOWEE_ID.eq(followee))
        .forShare()
        .fetchOptional(FOLLOWS.STATUS);
  }

  /**
   * フォロー解除・リクエストの取り消し。
   *
   * @return 消した行の状態（accepted・pending）。なければ空
   */
  public Optional<String> delete(long follower, long followee) {
    return dsl.deleteFrom(FOLLOWS)
        .where(FOLLOWS.FOLLOWER_ID.eq(follower))
        .and(FOLLOWS.FOLLOWEE_ID.eq(followee))
        .returning(FOLLOWS.STATUS)
        .fetchOptional(FOLLOWS.STATUS);
  }

  /** フォロー中の数（承認済みだけ） */
  public int countFollowing(long userId) {
    return dsl.fetchCount(FOLLOWS, FOLLOWS.FOLLOWER_ID.eq(userId).and(FOLLOWS.STATUS.eq(ACCEPTED)));
  }

  /** フォロワーの数（承認済みだけ） */
  public int countFollowers(long userId) {
    return dsl.fetchCount(FOLLOWS, FOLLOWS.FOLLOWEE_ID.eq(userId).and(FOLLOWS.STATUS.eq(ACCEPTED)));
  }

  /** 自分へのフォローのリクエストの数（A-121 の followRequestCount） */
  public int countPendingRequests(long followee) {
    return dsl.fetchCount(
        FOLLOWS, FOLLOWS.FOLLOWEE_ID.eq(followee).and(FOLLOWS.STATUS.eq(PENDING)));
  }

  /** フォロワー一覧（A-73）。承認された日時の新しい順に、件数まで */
  public List<FollowUserRow> findFollowers(long userId, long me, Cursor.Position after, int limit) {
    return findList(FOLLOWS.FOLLOWEE_ID, FOLLOWS.FOLLOWER_ID, userId, me, after, limit);
  }

  /** フォロー中一覧（A-72）。承認された日時の新しい順に、件数まで */
  public List<FollowUserRow> findFollowing(long userId, long me, Cursor.Position after, int limit) {
    return findList(FOLLOWS.FOLLOWER_ID, FOLLOWS.FOLLOWEE_ID, userId, me, after, limit);
  }

  /**
   * @param owner 一覧の持ち主の列（フォロワー一覧なら followee_id）
   * @param listed 一覧に並ぶ人の列（フォロワー一覧なら follower_id）
   */
  private List<FollowUserRow> findList(
      TableField<?, Long> owner,
      TableField<?, Long> listed,
      long userId,
      long me,
      Cursor.Position after,
      int limit) {
    Condition afterCursor =
        after == null
            ? noCondition()
            : row(FOLLOWS.ACCEPTED_AT, FOLLOWS.ID).lt(after.createdAt(), after.id());
    return dsl.select(
            FOLLOWS.ID,
            FOLLOWS.ACCEPTED_AT,
            USERS.USERNAME,
            USERS.DISPLAY_NAME,
            USERS.IS_PRIVATE,
            USERS.BIO,
            MINE.STATUS,
            field(USERS.ID.eq(me)))
        .from(FOLLOWS)
        .join(USERS)
        .on(USERS.ID.eq(listed))
        .leftJoin(MINE)
        .on(MINE.FOLLOWER_ID.eq(me).and(MINE.FOLLOWEE_ID.eq(USERS.ID)))
        .where(owner.eq(userId))
        .and(FOLLOWS.STATUS.eq(ACCEPTED))
        .and(afterCursor)
        .orderBy(FOLLOWS.ACCEPTED_AT.desc(), FOLLOWS.ID.desc())
        .limit(limit)
        .fetch(Records.mapping(FollowUserRow::new));
  }
}
