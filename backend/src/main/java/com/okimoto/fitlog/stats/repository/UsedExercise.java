package com.okimoto.fitlog.stats.repository;

import com.okimoto.fitlog.jooq.tables.records.ExercisesRecord;
import java.time.LocalDate;

/**
 * 自分が記録した種目（A-95 の要素）。
 *
 * @param exercise 種目
 * @param lastPerformedOn その種目を最後に記録した実施日
 */
public record UsedExercise(ExercisesRecord exercise, LocalDate lastPerformedOn) {}
