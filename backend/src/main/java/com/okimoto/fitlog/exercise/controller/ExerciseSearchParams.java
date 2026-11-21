package com.okimoto.fitlog.exercise.controller;

import com.okimoto.fitlog.common.controller.TextLength;
import jakarta.validation.constraints.Pattern;

/**
 * 種目の検索の条件（A-30）。
 *
 * @param q 名前の部分一致（30文字まで）。空白だけなら絞らない
 * @param bodyPart 部位。なければ絞らない
 */
public record ExerciseSearchParams(
    @TextLength(max = 30, message = "検索する文字は30文字以内で入力してください") String q,
    @Pattern(regexp = ExerciseRequests.BODY_PART, message = "部位の指定が正しくありません") String bodyPart) {}
