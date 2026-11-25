package com.okimoto.fitlog.comment.repository;

import static com.okimoto.fitlog.jooq.Tables.COMMENTS;
import static com.okimoto.fitlog.jooq.Tables.USERS;
import static com.okimoto.fitlog.jooq.Tables.WORKOUTS;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.row;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectCount;

import com.okimoto.fitlog.common.calc.Cursor;
import com.okimoto.fitlog.jooq.tables.Comments;
import com.okimoto.fitlog.jooq.tables.Users;
import com.okimoto.fitlog.workout.repository.WorkoutVisibility;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record12;
import org.jooq.Records;
import org.jooq.SelectOnConditionStep;
import org.springframework.stereotype.Repository;

/**
 * コメント・返信（comments）の読み書き（docs/database.md「comments」）。
 *
 * <p>一覧は、コメント → 返信（先頭3件）を multiset で1回の SQL にまとめて取り、返信の数はサブクエリで数える（docs/database.md「N+1
 * 問題を防ぐ」）。閲覧の条件は、投稿を確かめる側（サービス）で先に見る
 */
@Repository
public class CommentRepository {

  /** 1件のコメントに入れる返信の数（docs/api.md「Comment」） */
  public static final int REPLIES_IN_COMMENT = 3;

  /** 返信でないコメント・その書いた人 */
  private static final Comments C = COMMENTS.as("c");

  private static final Users CA = USERS.as("ca");

  /** 返信・その書いた人・返信先の人 */
  private static final Comments R = COMMENTS.as("r");

  private static final Users RA = USERS.as("ra");
  private static final Users RT = USERS.as("rt");

  /** 返信の数を数える別名 */
  private static final Comments RC = COMMENTS.as("rc");

  private final DSLContext dsl;

  public CommentRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * コメント・返信を保存する。返信は1段だけ・同じ投稿のコメントであることは、呼ぶ側で確かめる。
   *
   * @return 保存したコメントの ID
   */
  public long insert(NewComment comment) {
    return dsl.insertInto(COMMENTS)
        .set(COMMENTS.WORKOUT_ID, comment.workoutId())
        .set(COMMENTS.USER_ID, comment.userId())
        .set(COMMENTS.PARENT_ID, comment.parentId())
        .set(COMMENTS.REPLY_TO_USER_ID, comment.replyToUserId())
        .set(COMMENTS.BODY, comment.body())
        .set(COMMENTS.CREATED_AT, comment.createdAt())
        .returning(COMMENTS.ID)
        .fetchSingle()
        .getId();
  }

  /**
   * 返信・削除・返信の続きの対象のコメント。見てよい投稿（{@link WorkoutVisibility}）のコメントだけ。コメントの行を共有ロック（FOR
   * SHARE）して、確かめてから書き込むまでの間に消されないようにする。
   *
   * @return 見てはいけない・ないコメントは空
   */
  public Optional<CommentTarget> findVisibleTarget(long commentId, long me) {
    return dsl.select(
            COMMENTS.ID,
            COMMENTS.WORKOUT_ID,
            WORKOUTS.USER_ID,
            COMMENTS.PARENT_ID,
            COMMENTS.USER_ID)
        .from(COMMENTS)
        .join(WORKOUTS)
        .on(WORKOUTS.ID.eq(COMMENTS.WORKOUT_ID))
        .join(USERS)
        .on(USERS.ID.eq(WORKOUTS.USER_ID))
        .where(COMMENTS.ID.eq(commentId))
        .and(WorkoutVisibility.visibleTo(me))
        .forShare()
        .of(COMMENTS)
        .fetchOptional(Records.mapping(CommentTarget::new));
  }

  /**
   * コメント一覧（A-60）。返信でないコメントを古い順（同じ日時なら ID の小さい順）に件数まで。返信を古い順に3件までと、返信の数を入れる。
   *
   * @param after この位置より後（新しい）ものを返す。最初のページは null
   */
  public List<CommentRow> findPage(long workoutId, long me, Cursor.Position after, int limit) {
    Condition afterCursor =
        after == null ? noCondition() : row(C.CREATED_AT, C.ID).gt(after.createdAt(), after.id());
    return dsl.select(
            C.ID,
            CA.USERNAME,
            CA.DISPLAY_NAME,
            CA.IS_PRIVATE,
            CA.ICON_KEY,
            C.BODY,
            C.CREATED_AT,
            deletable(C.USER_ID, me),
            multiset(
                    replies(me)
                        .where(R.PARENT_ID.eq(C.ID))
                        .orderBy(R.CREATED_AT, R.ID)
                        .limit(REPLIES_IN_COMMENT))
                .convertFrom(r -> r.map(Records.mapping(CommentRepository::reply))),
            field(selectCount().from(RC).where(RC.PARENT_ID.eq(C.ID))))
        .from(C)
        .join(CA)
        .on(CA.ID.eq(C.USER_ID))
        .join(WORKOUTS)
        .on(WORKOUTS.ID.eq(C.WORKOUT_ID))
        .where(C.WORKOUT_ID.eq(workoutId))
        .and(C.PARENT_ID.isNull())
        .and(afterCursor)
        .orderBy(C.CREATED_AT, C.ID)
        .limit(limit)
        .fetch(
            Records.mapping(
                (id,
                    username,
                    displayName,
                    isPrivate,
                    iconKey,
                    body,
                    createdAt,
                    deletable,
                    replies,
                    replyCount) ->
                    new CommentRow(
                        id,
                        new CommentUser(username, displayName, isPrivate, iconKey),
                        body,
                        createdAt,
                        deletable,
                        replies,
                        replyCount)));
  }

  /**
   * 返信の続き（A-65）。その返信元への返信を古い順（同じ日時なら ID の小さい順）に件数まで。
   *
   * @param after この位置より後（新しい）ものを返す。最初のページは null
   */
  public List<ReplyRow> findReplies(long parentId, long me, Cursor.Position after, int limit) {
    Condition afterCursor =
        after == null ? noCondition() : row(R.CREATED_AT, R.ID).gt(after.createdAt(), after.id());
    // replies は他の SQL の中（multiset）でも使うので、DB につながない形で組み立て、ここで実行する
    return dsl.fetch(
            replies(me)
                .join(WORKOUTS)
                .on(WORKOUTS.ID.eq(R.WORKOUT_ID))
                .where(R.PARENT_ID.eq(parentId))
                .and(afterCursor)
                .orderBy(R.CREATED_AT, R.ID)
                .limit(limit))
        .map(Records.mapping(CommentRepository::reply));
  }

  /** 書いたコメント・返信の1件を、返信の形で読む（A-61 の応答）。返信でなければ返信先は null */
  public Optional<ReplyRow> findOne(long commentId, long me) {
    return dsl.fetchOptional(
            replies(me).join(WORKOUTS).on(WORKOUTS.ID.eq(R.WORKOUT_ID)).where(R.ID.eq(commentId)))
        .map(Records.mapping(CommentRepository::reply));
  }

  /** その投稿のコメント数（返信を含む） */
  public int count(long workoutId) {
    return dsl.fetchCount(COMMENTS, COMMENTS.WORKOUT_ID.eq(workoutId));
  }

  /** コメントを消す。返信元なら、返信も外部キーの CASCADE で消える。通知も消える */
  public void delete(long commentId) {
    dsl.deleteFrom(COMMENTS).where(COMMENTS.ID.eq(commentId)).execute();
  }

  /** まだ残っているか。通知を作る直前に確かめる。行をロック（FOR SHARE）して、通知を作り終えるまで削除を待たせる */
  public boolean lockExists(long commentId) {
    return dsl.selectOne()
        .from(COMMENTS)
        .where(COMMENTS.ID.eq(commentId))
        .forShare()
        .fetchOptional()
        .isPresent();
  }

  /** 返信の列（ReplyRow の順）。投稿者を見るため、使う側で workouts を結合しておく */
  private static SelectOnConditionStep<
          Record12<
              Long,
              String,
              String,
              Boolean,
              String,
              String,
              String,
              Boolean,
              String,
              String,
              OffsetDateTime,
              Boolean>>
      replies(long me) {
    return select(
            R.ID,
            RA.USERNAME,
            RA.DISPLAY_NAME,
            RA.IS_PRIVATE,
            RA.ICON_KEY,
            RT.USERNAME,
            RT.DISPLAY_NAME,
            RT.IS_PRIVATE,
            RT.ICON_KEY,
            R.BODY,
            R.CREATED_AT,
            deletable(R.USER_ID, me))
        .from(R)
        .join(RA)
        .on(RA.ID.eq(R.USER_ID))
        .leftJoin(RT)
        .on(RT.ID.eq(R.REPLY_TO_USER_ID));
  }

  private static ReplyRow reply(
      Long id,
      String username,
      String displayName,
      Boolean isPrivate,
      String iconKey,
      String replyToUsername,
      String replyToDisplayName,
      Boolean replyToPrivate,
      String replyToIconKey,
      String body,
      OffsetDateTime createdAt,
      Boolean deletable) {
    return new ReplyRow(
        id,
        new CommentUser(username, displayName, isPrivate, iconKey),
        // 返信先の人が退会していれば NULL（結合しても行がない）
        replyToUsername == null
            ? null
            : new CommentUser(replyToUsername, replyToDisplayName, replyToPrivate, replyToIconKey),
        body,
        createdAt,
        deletable);
  }

  /** 見ている人が消せるか：書いた本人か、投稿者（docs/api.md A-62）。使う SQL は workouts を結合しておく */
  private static Field<Boolean> deletable(Field<Long> writer, long me) {
    return field(writer.eq(me).or(WORKOUTS.USER_ID.eq(me)));
  }
}
