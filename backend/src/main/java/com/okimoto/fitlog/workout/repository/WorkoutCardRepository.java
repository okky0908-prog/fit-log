package com.okimoto.fitlog.workout.repository;

import static com.okimoto.fitlog.jooq.Tables.EXERCISES;
import static com.okimoto.fitlog.jooq.Tables.USERS;
import static com.okimoto.fitlog.jooq.Tables.WORKOUTS;
import static com.okimoto.fitlog.jooq.Tables.WORKOUT_EXERCISES;
import static com.okimoto.fitlog.jooq.Tables.WORKOUT_IMAGES;
import static com.okimoto.fitlog.jooq.Tables.WORKOUT_SETS;
import static org.jooq.impl.DSL.arrayAgg;
import static org.jooq.impl.DSL.arrayGet;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.min;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.row;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectCount;
import static org.jooq.impl.DSL.sum;
import static org.jooq.impl.DSL.when;

import com.okimoto.fitlog.common.calc.Cursor;
import com.okimoto.fitlog.workout.calc.ExerciseAggregate;
import java.math.BigDecimal;
import java.util.List;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Records;
import org.jooq.SortField;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

/**
 * 記録の一覧（タイムライン・プロフィールの記録一覧。docs/api.md「WorkoutCard」）。1ページを1回の SQL で取る。
 *
 * <p>種目は、先頭5種目だけを、種目ごとにセットを集計した値で返す（セットの行は取らない。docs/database.md「N+1 問題を防ぐ」）。 読むすべての SQL
 * で、閲覧の条件（{@link WorkoutVisibility}）を使う
 */
@Repository
public class WorkoutCardRepository {

  /** 種目の要約を返す種目の数（6種目目からは数だけ） */
  static final int SUMMARY_EXERCISES = 5;

  /** 最大のセットの順：回数が1回以上のセットを先に、重量が重い順、回数が多い順、並び順の先。全セット0回なら一番重いセットになる。 回数のみの種目では重量がないので、回数が多い順になる */
  private static final SortField<?>[] BEST_SET_ORDER = {
    when(WORKOUT_SETS.REPS.gt(0), inline(1)).otherwise(inline(0)).desc(),
    WORKOUT_SETS.WEIGHT_KG.desc().nullsLast(),
    WORKOUT_SETS.REPS.desc(),
    WORKOUT_SETS.SORT_ORDER.asc()
  };

  private final DSLContext dsl;

  public WorkoutCardRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * タイムライン（A-50・A-51）。投稿日時の新しい順（同じなら ID の大きい順）。
   *
   * @param followingOnly true ならフォロー中タイムライン（自分と承認済みのフォロー相手の記録だけ）
   * @param after この位置より後から返す。最初のページは null
   */
  public List<WorkoutCardRow> findTimeline(
      long me, boolean followingOnly, Cursor.Position after, int limit) {
    Condition afterCursor =
        after == null
            ? noCondition()
            : row(WORKOUTS.CREATED_AT, WORKOUTS.ID).lt(after.createdAt(), after.id());
    return fetchVisible(
        me,
        afterCursor.and(followingOnly ? WorkoutVisibility.byMeOrFollowee(me) : noCondition()),
        List.of(WORKOUTS.CREATED_AT.desc(), WORKOUTS.ID.desc()),
        limit);
  }

  /**
   * そのユーザーの記録一覧（A-81）。実施日の新しい順（同じ日は投稿日時の新しい順、それも同じなら ID の大きい順）。
   *
   * @param after この位置より後から返す。最初のページは null
   */
  public List<WorkoutCardRow> findByUser(
      long owner, long me, Cursor.DatedPosition after, int limit) {
    Condition afterCursor =
        after == null
            ? noCondition()
            : row(WORKOUTS.PERFORMED_ON, WORKOUTS.CREATED_AT, WORKOUTS.ID)
                .lt(after.performedOn(), after.createdAt(), after.id());
    return fetchVisible(
        me,
        afterCursor.and(WORKOUTS.USER_ID.eq(owner)),
        List.of(WORKOUTS.PERFORMED_ON.desc(), WORKOUTS.CREATED_AT.desc(), WORKOUTS.ID.desc()),
        limit);
  }

  /**
   * 新しい投稿の件数（A-52・A-53）。since より ID が大きい、見てよい他人の記録を、limit 件まで数える（それより多くは数えない）。
   *
   * @param followingOnly true ならフォロー中タイムラインに出る記録だけ
   */
  public int countNew(long me, boolean followingOnly, long since, int limit) {
    return dsl.fetchCount(
        dsl.select(inline(1))
            .from(WORKOUTS)
            .join(USERS)
            .on(USERS.ID.eq(WORKOUTS.USER_ID))
            .where(WORKOUTS.ID.gt(since))
            .and(WORKOUTS.USER_ID.ne(me))
            .and(WorkoutVisibility.visibleTo(me))
            .and(followingOnly ? WorkoutVisibility.byMeOrFollowee(me) : noCondition())
            .limit(limit));
  }

  /** 見てよい記録のうち、条件に合うものを並び順のとおりに limit 件まで、一覧の1件の形で取る */
  private List<WorkoutCardRow> fetchVisible(
      long me, Condition condition, List<SortField<?>> orderBy, int limit) {
    return dsl.select(
            WORKOUTS.ID,
            WORKOUTS.USER_ID,
            USERS.USERNAME,
            USERS.DISPLAY_NAME,
            USERS.IS_PRIVATE,
            USERS.ICON_KEY,
            WORKOUTS.PERFORMED_ON,
            WORKOUTS.CREATED_AT,
            WORKOUTS.EDITED_AT,
            WORKOUTS.BODY,
            WORKOUTS.VISIBILITY,
            exerciseSummaries(),
            field(
                selectCount()
                    .from(WORKOUT_EXERCISES)
                    .where(WORKOUT_EXERCISES.WORKOUT_ID.eq(WORKOUTS.ID))
                    .and(WORKOUT_EXERCISES.SORT_ORDER.gt((short) SUMMARY_EXERCISES))),
            multiset(
                    select(WORKOUT_IMAGES.ID, WORKOUT_IMAGES.IMAGE_KEY, WORKOUT_IMAGES.SORT_ORDER)
                        .from(WORKOUT_IMAGES)
                        .where(WORKOUT_IMAGES.WORKOUT_ID.eq(WORKOUTS.ID))
                        .orderBy(WORKOUT_IMAGES.SORT_ORDER))
                .convertFrom(r -> r.map(Records.mapping(WorkoutDetail.Image::new))),
            ReactionFields.likeCount(),
            ReactionFields.likedBy(me),
            ReactionFields.commentCount())
        .from(WORKOUTS)
        .join(USERS)
        .on(USERS.ID.eq(WORKOUTS.USER_ID))
        .where(WorkoutVisibility.visibleTo(me))
        .and(condition)
        .orderBy(orderBy)
        .limit(limit)
        .fetch(Records.mapping(WorkoutCardRow::new));
  }

  /** 先頭5種目を、種目ごとにセットを集計して返す（並び順のとおり） */
  private static Field<List<ExerciseAggregate>> exerciseSummaries() {
    // 全セットが同じ重量・回数か。時間・距離の種目は回数がないので false
    Field<Boolean> uniform =
        when(
                max(WORKOUT_SETS.REPS)
                    .eq(min(WORKOUT_SETS.REPS))
                    .and(
                        max(WORKOUT_SETS.WEIGHT_KG).isNotDistinctFrom(min(WORKOUT_SETS.WEIGHT_KG))),
                inline(true))
            .otherwise(inline(false));
    Field<BigDecimal> bestWeight =
        arrayGet(arrayAgg(WORKOUT_SETS.WEIGHT_KG).orderBy(BEST_SET_ORDER), 1);
    Field<Integer> bestReps = arrayGet(arrayAgg(WORKOUT_SETS.REPS).orderBy(BEST_SET_ORDER), 1);
    return multiset(
            select(
                    WORKOUT_EXERCISES.EXERCISE_ID,
                    EXERCISES.NAME,
                    EXERCISES.RECORD_TYPE,
                    count(),
                    uniform,
                    bestWeight,
                    bestReps,
                    sum(WORKOUT_SETS.REPS).cast(SQLDataType.INTEGER),
                    sum(WORKOUT_SETS.DURATION_SEC).cast(SQLDataType.INTEGER),
                    sum(WORKOUT_SETS.DISTANCE_KM))
                .from(WORKOUT_EXERCISES)
                .join(EXERCISES)
                .on(EXERCISES.ID.eq(WORKOUT_EXERCISES.EXERCISE_ID))
                .join(WORKOUT_SETS)
                .on(WORKOUT_SETS.WORKOUT_EXERCISE_ID.eq(WORKOUT_EXERCISES.ID))
                .where(WORKOUT_EXERCISES.WORKOUT_ID.eq(WORKOUTS.ID))
                .and(WORKOUT_EXERCISES.SORT_ORDER.le((short) SUMMARY_EXERCISES))
                .groupBy(
                    WORKOUT_EXERCISES.ID,
                    WORKOUT_EXERCISES.EXERCISE_ID,
                    EXERCISES.NAME,
                    EXERCISES.RECORD_TYPE,
                    WORKOUT_EXERCISES.SORT_ORDER)
                .orderBy(WORKOUT_EXERCISES.SORT_ORDER))
        .convertFrom(r -> r.map(Records.mapping(ExerciseAggregate::new)));
  }
}
