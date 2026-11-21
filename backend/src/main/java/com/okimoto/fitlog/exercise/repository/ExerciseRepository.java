package com.okimoto.fitlog.exercise.repository;

import static com.okimoto.fitlog.jooq.Tables.EXERCISES;
import static org.jooq.impl.DSL.noCondition;

import com.okimoto.fitlog.exercise.calc.ExerciseRules;
import com.okimoto.fitlog.jooq.tables.records.ExercisesRecord;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/** 種目（exercises）の読み書き */
@Repository
public class ExerciseRepository {

  /** 部位の並び順（胸・背中・脚・肩・腕・腹・全身・有酸素。docs/api.md A-30） */
  private static final String[] BODY_PART_ORDER = {
    "chest", "back", "legs", "shoulders", "arms", "abs", "full_body", "cardio"
  };

  private final DSLContext dsl;

  public ExerciseRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * 種目の検索（A-30）。最初から用意された種目と、その人の自分専用の種目だけを、部位の順 → 用意された種目の順（sort_order）→ 自分専用の種目の名前の順で返す。
   *
   * @param keyPattern 名前の部分一致の LIKE のパターン（{@link ExerciseRules#containsPattern}）。null なら絞らない
   * @param bodyPart 部位。null なら絞らない
   */
  public List<ExercisesRecord> search(long ownerId, String keyPattern, String bodyPart) {
    Condition byName =
        keyPattern == null
            ? noCondition()
            : EXERCISES.NAME_KEY.like(keyPattern, ExerciseRules.LIKE_ESCAPE);
    Condition byBodyPart = bodyPart == null ? noCondition() : EXERCISES.BODY_PART.eq(bodyPart);
    return dsl.selectFrom(EXERCISES)
        .where(EXERCISES.OWNER_ID.isNull().or(EXERCISES.OWNER_ID.eq(ownerId)))
        .and(byName)
        .and(byBodyPart)
        .orderBy(
            EXERCISES.BODY_PART.sortAsc(BODY_PART_ORDER),
            // 用意された種目（owner_id が NULL）を先に
            EXERCISES.OWNER_ID.nullsFirst(),
            EXERCISES.SORT_ORDER,
            EXERCISES.NAME_KEY,
            EXERCISES.ID)
        .fetch();
  }

  /**
   * その名前（キー）が、最初から用意された種目か、その人の自分専用の種目で使われているか。
   *
   * @param excludeId 名前を変える種目自身。作るときは null
   */
  public boolean nameTaken(long ownerId, String nameKey, Long excludeId) {
    Condition self = excludeId == null ? noCondition() : EXERCISES.ID.ne(excludeId);
    return dsl.fetchExists(
        EXERCISES,
        EXERCISES.NAME_KEY.eq(nameKey),
        EXERCISES.OWNER_ID.isNull().or(EXERCISES.OWNER_ID.eq(ownerId).and(self)));
  }

  /**
   * 記録で使える種目（最初から用意された種目と、その人の自分専用の種目）の記録方式。他人の自分専用の種目・ない種目は入らない
   *
   * @return 種目の ID → 記録方式
   */
  public Map<Long, String> findUsable(long ownerId, Collection<Long> ids) {
    return dsl.select(EXERCISES.ID, EXERCISES.RECORD_TYPE)
        .from(EXERCISES)
        .where(EXERCISES.ID.in(ids))
        .and(EXERCISES.OWNER_ID.isNull().or(EXERCISES.OWNER_ID.eq(ownerId)))
        .fetchMap(EXERCISES.ID, EXERCISES.RECORD_TYPE);
  }

  /** その人の自分専用の種目の数（100件の上限。数える前に users の行をロックしておく） */
  public int countCustom(long ownerId) {
    return dsl.fetchCount(EXERCISES, EXERCISES.OWNER_ID.eq(ownerId));
  }

  /** 自分専用の種目を保存し、保存した行を返す。ランニングにはしない（is_running は既定値の false） */
  public ExercisesRecord insertCustom(NewExercise exercise) {
    return dsl.insertInto(EXERCISES)
        .set(EXERCISES.OWNER_ID, exercise.ownerId())
        .set(EXERCISES.NAME, exercise.name())
        .set(EXERCISES.NAME_KEY, exercise.nameKey())
        .set(EXERCISES.BODY_PART, exercise.bodyPart())
        .set(EXERCISES.RECORD_TYPE, exercise.recordType())
        .set(EXERCISES.METS, exercise.mets())
        .set(EXERCISES.CREATED_AT, exercise.createdAt())
        .set(EXERCISES.UPDATED_AT, exercise.createdAt())
        .returning()
        .fetchSingle();
  }

  /** その人の自分専用の種目。他人の種目・最初から用意された種目は空 */
  public Optional<ExercisesRecord> findCustom(long id, long ownerId) {
    return dsl.selectFrom(EXERCISES)
        .where(EXERCISES.ID.eq(id))
        .and(EXERCISES.OWNER_ID.eq(ownerId))
        .fetchOptional();
  }

  /** 自分専用の種目の名前・部位・METs を変え、変えたあとの行を返す。記録方式は変えない（F-16） */
  public ExercisesRecord updateCustom(
      long id, String name, String nameKey, String bodyPart, BigDecimal mets, OffsetDateTime now) {
    return dsl.update(EXERCISES)
        .set(EXERCISES.NAME, name)
        .set(EXERCISES.NAME_KEY, nameKey)
        .set(EXERCISES.BODY_PART, bodyPart)
        .set(EXERCISES.METS, mets)
        .set(EXERCISES.UPDATED_AT, now)
        .where(EXERCISES.ID.eq(id))
        .returning()
        .fetchSingle();
  }

  /** 自分専用の種目を消す。テンプレートの中のその種目は、外部キーの CASCADE で一緒に消える */
  public void deleteCustom(long id) {
    dsl.deleteFrom(EXERCISES).where(EXERCISES.ID.eq(id)).execute();
  }
}
