package com.okimoto.fitlog.common.controller;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 前後の空白・改行を除いたあとの文字数が min〜max かを確かめる（docs/test-plan.md 5.1）。
 *
 * <p>標準の {@code @Size} は UTF-16 の数で数えるので、絵文字が2文字になる。ここではコードポイントで数え、画面の {@code [...text].length}
 * と同じにする。null は、min が1以上ならエラー
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TextLength.Validator.class)
public @interface TextLength {

  int min() default 0;

  int max();

  String message();

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator implements ConstraintValidator<TextLength, String> {

    private int min;
    private int max;

    @Override
    public void initialize(TextLength annotation) {
      this.min = annotation.min();
      this.max = annotation.max();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
      if (value == null) {
        return min <= 0;
      }
      String text = value.strip();
      int length = text.codePointCount(0, text.length());
      return length >= min && length <= max;
    }
  }
}
