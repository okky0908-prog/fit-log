package com.okimoto.fitlog.stats.controller;

import com.okimoto.fitlog.auth.config.AuthenticatedUser;
import com.okimoto.fitlog.common.controller.ApiException;
import com.okimoto.fitlog.common.controller.ErrorCode;
import com.okimoto.fitlog.exercise.controller.ExerciseResponse;
import com.okimoto.fitlog.stats.calc.TrendRules;
import com.okimoto.fitlog.stats.calc.TrendRules.Point;
import com.okimoto.fitlog.stats.calc.TrendRules.Range;
import com.okimoto.fitlog.stats.repository.BestWeight;
import com.okimoto.fitlog.stats.service.TrendService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 種目ごとの推移（docs/api.md A-95・A-96）。本人の記録だけを返す */
@RestController
@RequestMapping("/api/stats/exercises")
public class TrendController {

  private final TrendService trendService;

  public TrendController(TrendService trendService) {
    this.trendService = trendService;
  }

  /** A-95 の要素 */
  public record UsedExerciseResponse(ExerciseResponse exercise, LocalDate lastPerformedOn) {}

  /** 全期間の自己ベスト */
  public record PersonalBestResponse(BigDecimal weightKg, LocalDate performedOn, long workoutId) {

    static PersonalBestResponse of(BestWeight best) {
      return best == null
          ? null
          : new PersonalBestResponse(best.weightKg(), best.performedOn(), best.workoutId());
    }
  }

  /** 全期間の推定 1RM の最高 */
  public record BestE1rmResponse(BigDecimal e1rmKg, LocalDate performedOn, long workoutId) {

    static BestE1rmResponse of(BestWeight best) {
      return best == null
          ? null
          : new BestE1rmResponse(best.weightKg(), best.performedOn(), best.workoutId());
    }
  }

  /** A-96 の応答 */
  public record TrendResponse(
      ExerciseResponse exercise,
      PersonalBestResponse personalBest,
      BestE1rmResponse bestE1rm,
      List<Point> points) {}

  /** A-95 自分が1回以上記録した種目（部位の順。最後に記録した日つき） */
  @GetMapping
  public List<UsedExerciseResponse> exercises(@AuthenticationPrincipal AuthenticatedUser user) {
    return trendService.exercises(user.id()).stream()
        .map(used ->
            new UsedExerciseResponse(
                ExerciseResponse.from(used.exercise()), used.lastPerformedOn()))
        .toList();
  }

  /** A-96 種目ごとの推移と自己ベスト。range は 3m・1y・all（なければ 3m。それ以外は 400） */
  @GetMapping("/{exerciseId}")
  public TrendResponse trend(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable long exerciseId,
      @RequestParam(defaultValue = "3m") String range) {
    Range parsed =
        TrendRules.rangeOf(range).orElseThrow(() -> new ApiException(ErrorCode.MALFORMED_REQUEST));
    TrendService.Trend trend = trendService.trend(user.id(), exerciseId, parsed);
    return new TrendResponse(
        ExerciseResponse.from(trend.exercise()),
        PersonalBestResponse.of(trend.personalBest()),
        BestE1rmResponse.of(trend.bestE1rm()),
        trend.points());
  }
}
