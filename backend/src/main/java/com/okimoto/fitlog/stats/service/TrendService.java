package com.okimoto.fitlog.stats.service;

import com.okimoto.fitlog.common.calc.JapanTime;
import com.okimoto.fitlog.common.controller.ApiException;
import com.okimoto.fitlog.common.controller.ErrorCode;
import com.okimoto.fitlog.exercise.repository.ExerciseRepository;
import com.okimoto.fitlog.jooq.tables.records.ExercisesRecord;
import com.okimoto.fitlog.stats.calc.TrendRules;
import com.okimoto.fitlog.stats.calc.TrendRules.Point;
import com.okimoto.fitlog.stats.calc.TrendRules.Range;
import com.okimoto.fitlog.stats.repository.BestWeight;
import com.okimoto.fitlog.stats.repository.TrendRepository;
import com.okimoto.fitlog.stats.repository.UsedExercise;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 種目ごとの推移（F-84）と自己ベスト（F-85）。docs/api.md A-95・A-96。本人の記録だけを、公開範囲に関係なく集計する */
@Service
public class TrendService {

  private static final String WEIGHT_REPS = "weight_reps";

  private final TrendRepository trendRepository;
  private final ExerciseRepository exerciseRepository;
  private final Clock clock;

  public TrendService(
      TrendRepository trendRepository, ExerciseRepository exerciseRepository, Clock clock) {
    this.trendRepository = trendRepository;
    this.exerciseRepository = exerciseRepository;
    this.clock = clock;
  }

  /**
   * 種目ごとの推移。
   *
   * @param exercise 種目
   * @param personalBest 全期間の自己ベスト。重量 × 回数の種目でなければ・なければ null
   * @param bestE1rm 全期間の推定 1RM の最高。重量 × 回数の種目でなければ・なければ null
   * @param points 期間の、実施日ごとの点（古い順）
   */
  public record Trend(
      ExercisesRecord exercise, BestWeight personalBest, BestWeight bestE1rm, List<Point> points) {}

  /** A-95 自分が1回以上記録した種目 */
  @Transactional(readOnly = true)
  public List<UsedExercise> exercises(long me) {
    return trendRepository.usedExercises(me);
  }

  /**
   * A-96 種目ごとの推移。他人の自分専用の種目・存在しない種目は 404。記録していない種目は、点を空にして返す
   *
   * @param range 期間（今日（日本時間）から数える）
   */
  @Transactional(readOnly = true)
  public Trend trend(long me, long exerciseId, Range range) {
    ExercisesRecord exercise =
        exerciseRepository
            .findVisible(exerciseId, me)
            .orElseThrow(() -> new ApiException(ErrorCode.EXERCISE_NOT_FOUND));
    String recordType = exercise.getRecordType();
    LocalDate from = TrendRules.fromOf(range, JapanTime.today(clock)).orElse(null);
    List<Point> points =
        trendRepository.days(me, exerciseId, recordType, from).stream()
            .map(day -> TrendRules.point(recordType, exercise.getIsRunning(), day))
            .toList();
    if (!WEIGHT_REPS.equals(recordType)) {
      return new Trend(exercise, null, null, points);
    }
    return new Trend(
        exercise,
        trendRepository.personalBest(me, exerciseId).orElse(null),
        trendRepository.bestE1rm(me, exerciseId).orElse(null),
        points);
  }
}
