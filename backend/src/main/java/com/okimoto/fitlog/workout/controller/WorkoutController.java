package com.okimoto.fitlog.workout.controller;

import com.okimoto.fitlog.auth.config.AuthenticatedUser;
import com.okimoto.fitlog.workout.service.WorkoutInput;
import com.okimoto.fitlog.workout.service.WorkoutService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 記録の API（docs/api.md A-20・A-21）。編集・削除（A-22・A-23）は次の PR で足す */
@RestController
@RequestMapping("/api/workouts")
public class WorkoutController {

  private final WorkoutService workoutService;

  public WorkoutController(WorkoutService workoutService) {
    this.workoutService = workoutService;
  }

  /**
   * A-20 記録の作成。画像を一緒に送るので multipart/form-data で受け取り、記録の JSON は {@code data} のパートに入れる。画像（{@code
   * images} のパート）は、画像の PR で足す
   */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public WorkoutResponse create(
      @AuthenticationPrincipal AuthenticatedUser user, @RequestPart("data") WorkoutInput data) {
    return WorkoutResponse.from(workoutService.create(user.id(), data), user.id());
  }

  /** A-21 記録の詳細 */
  @GetMapping("/{workoutId}")
  public WorkoutResponse detail(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable long workoutId) {
    return WorkoutResponse.from(workoutService.find(user.id(), workoutId), user.id());
  }
}
