package com.okimoto.fitlog.common.controller;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

/**
 * UTF-8 にしたときのバイト数が max 以下かを確かめる。BCrypt が72バイトまでしか扱えないパスワードに使う（日本語は1文字3バイト）。
 *
 * <p>文字数の上限（{@code @Size(max)}）と同じ値と組み合わせる前提で、文字数が max を超えるときと null は確かめない（そちらの決まりで止める）
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxBytes.Validator.class)
public @interface MaxBytes {

  int value();

  String message();

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator implements ConstraintValidator<MaxBytes, String> {

    private int max;

    @Override
    public void initialize(MaxBytes annotation) {
      this.max = annotation.value();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
      // @Size と同じく UTF-16 の数で比べる。それが上限を超えていれば、バイト数も必ず超える。@Size のエラーだけを出し、同じ欄に2つ出さない
      if (value == null || value.length() > max) {
        return true;
      }
      return value.getBytes(StandardCharsets.UTF_8).length <= max;
    }
  }
}
