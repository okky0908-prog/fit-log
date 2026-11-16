package com.okimoto.fitlog.exercise.controller;

import com.okimoto.fitlog.exercise.repository.ExerciseRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 種目の API
 *
 * <p>段階 0 では、A-30（種目の検索）のうち、最初から用意された種目の一覧だけを返す。ログイン・自分専用の種目・検索の条件（q・bodyPart）は段階 1 で足す。
 */
@RestController
@RequestMapping("/api/exercises")
public class ExerciseController {

  private final ExerciseRepository exerciseRepository;

  public ExerciseController(ExerciseRepository exerciseRepository) {
    this.exerciseRepository = exerciseRepository;
  }

  /** A-30 種目の検索 */
  @GetMapping
  public List<ExerciseResponse> search() {
    return exerciseRepository.findPresets().stream().map(ExerciseResponse::from).toList();
  }
}
