package com.okimoto.fitlog.workout.controller;

import com.okimoto.fitlog.auth.config.AuthenticatedUser;
import com.okimoto.fitlog.image.service.ImageUrls;
import com.okimoto.fitlog.workout.repository.WorkoutDetail;
import com.okimoto.fitlog.workout.service.WorkoutInput;
import com.okimoto.fitlog.workout.service.WorkoutService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 記録の API（docs/api.md A-20〜A-23） */
@RestController
@RequestMapping("/api/workouts")
public class WorkoutController {

  private final WorkoutService workoutService;
  private final ImageUrls imageUrls;

  public WorkoutController(WorkoutService workoutService, ImageUrls imageUrls) {
    this.workoutService = workoutService;
    this.imageUrls = imageUrls;
  }

  /**
   * A-20 記録の作成。画像を一緒に送るので multipart/form-data で受け取り、記録の JSON は {@code data} のパート、画像は {@code images}
   * のパート（0〜4個。選んだ順）に入れる
   */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public WorkoutResponse create(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestPart("data") WorkoutInput data,
      @RequestPart(name = "images", required = false) List<MultipartFile> images) {
    return response(workoutService.create(user.id(), data, contents(images)), user.id());
  }

  /** A-21 記録の詳細 */
  @GetMapping("/{workoutId}")
  public WorkoutResponse detail(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable long workoutId) {
    return response(workoutService.find(user.id(), workoutId), user.id());
  }

  /**
   * A-22 記録の編集。作成と同じ形（multipart）。{@code data} に残す画像（{@code keepImageIds}）、{@code images} に新しい画像を入れる
   */
  @PutMapping(path = "/{workoutId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public WorkoutResponse update(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable long workoutId,
      @RequestPart("data") WorkoutInput data,
      @RequestPart(name = "images", required = false) List<MultipartFile> images) {
    return response(workoutService.update(user.id(), workoutId, data, contents(images)), user.id());
  }

  /** A-23 記録の削除 */
  @DeleteMapping("/{workoutId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable long workoutId) {
    workoutService.delete(user.id(), workoutId);
  }

  /** 閲覧を許可した記録（Service で確かめたもの）なので、ここで画像の署名付き URL を作る */
  private WorkoutResponse response(WorkoutDetail detail, long me) {
    return WorkoutResponse.from(detail, me, imageUrls::urlOf, imageUrls::iconUrlOf);
  }

  private static List<byte[]> contents(List<MultipartFile> images) {
    if (images == null) {
      return List.of();
    }
    List<byte[]> contents = new ArrayList<>();
    for (MultipartFile image : images) {
      try {
        contents.add(image.getBytes());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return contents;
  }
}
