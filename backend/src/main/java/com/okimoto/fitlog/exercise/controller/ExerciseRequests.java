package com.okimoto.fitlog.exercise.controller;

import com.okimoto.fitlog.common.controller.TextLength;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 自分専用の種目のリクエスト（A-32・A-33）。入力の決まりは docs/features.md「種目（マスタ）」 */
final class ExerciseRequests {

  static final String BODY_PART = "chest|back|legs|shoulders|arms|abs|full_body|cardio";
  static final String RECORD_TYPE = "weight_reps|reps|time_distance";
  static final String NAME_MESSAGE = "種目の名前は1〜30文字で入力してください";
  static final String BODY_PART_MESSAGE = "部位を選んでください";
  static final String RECORD_TYPE_MESSAGE = "記録方式を選んでください";

  private ExerciseRequests() {}

  /**
   * A-32 自分専用の種目の追加。
   *
   * @param name 名前（前後の空白を除いて1〜30文字）
   * @param bodyPart 部位
   * @param recordType 記録方式（あとから変えられない）
   */
  record Create(
      @TextLength(min = 1, max = 30, message = NAME_MESSAGE) String name,
      @NotNull(message = BODY_PART_MESSAGE)
          @Pattern(regexp = BODY_PART, message = BODY_PART_MESSAGE)
          String bodyPart,
      @NotNull(message = RECORD_TYPE_MESSAGE)
          @Pattern(regexp = RECORD_TYPE, message = RECORD_TYPE_MESSAGE)
          String recordType) {}

  /**
   * A-33 自分専用の種目の名前・部位の変更。記録方式は変えられないので受け取らない。
   *
   * @param name 名前
   * @param bodyPart 部位
   */
  record Update(
      @TextLength(min = 1, max = 30, message = NAME_MESSAGE) String name,
      @NotNull(message = BODY_PART_MESSAGE)
          @Pattern(regexp = BODY_PART, message = BODY_PART_MESSAGE)
          String bodyPart) {}
}
