package com.okimoto.fitlog.exercise.repository;

import static com.okimoto.fitlog.jooq.Tables.EXERCISES;

import com.okimoto.fitlog.jooq.tables.records.ExercisesRecord;
import java.util.List;
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

  /** 最初から用意された種目を、部位の順 → 部位の中の順（sort_order）で返す */
  public List<ExercisesRecord> findPresets() {
    return dsl.selectFrom(EXERCISES)
        .where(EXERCISES.OWNER_ID.isNull())
        .orderBy(EXERCISES.BODY_PART.sortAsc(BODY_PART_ORDER), EXERCISES.SORT_ORDER)
        .fetch();
  }
}
