package com.okimoto.fitlog.exercise.controller;

import com.okimoto.fitlog.auth.config.AuthenticatedUser;
import com.okimoto.fitlog.exercise.service.ExerciseService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 種目の API（docs/api.md A-30・A-32〜A-34）。
 *
 * <p>A-31（最近使った種目）は、記録の表を作るときに足す
 */
@RestController
@RequestMapping("/api/exercises")
public class ExerciseController {

  private final ExerciseService exerciseService;

  public ExerciseController(ExerciseService exerciseService) {
    this.exerciseService = exerciseService;
  }

  /** A-30 種目の検索 */
  @GetMapping
  public List<ExerciseResponse> search(
      @AuthenticationPrincipal AuthenticatedUser user, @Valid ExerciseSearchParams params) {
    return exerciseService.search(user.id(), params.q(), params.bodyPart()).stream()
        .map(ExerciseResponse::from)
        .toList();
  }

  /** A-32 自分専用の種目の追加 */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ExerciseResponse create(
      @AuthenticationPrincipal AuthenticatedUser user,
      @Valid @RequestBody ExerciseRequests.Create request) {
    return ExerciseResponse.from(
        exerciseService.create(
            user.id(), request.name(), request.bodyPart(), request.recordType()));
  }

  /** A-33 自分専用の種目の名前・部位の変更 */
  @PutMapping("/{exerciseId}")
  public ExerciseResponse update(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable long exerciseId,
      @Valid @RequestBody ExerciseRequests.Update request) {
    return ExerciseResponse.from(
        exerciseService.update(user.id(), exerciseId, request.name(), request.bodyPart()));
  }

  /** A-34 自分専用の種目の削除 */
  @DeleteMapping("/{exerciseId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable long exerciseId) {
    exerciseService.delete(user.id(), exerciseId);
  }
}
