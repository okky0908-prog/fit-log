package com.okimoto.fitlog.workout.service;

import com.okimoto.fitlog.workout.calc.SetInput;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 記録の作成・編集の入力（A-20・A-22 の {@code data} のパート）。
 *
 * <p>入力チェックは {@link WorkoutInputValidator} が、項目をまたいでまとめて行う（画面が全部の欄に赤字を出せるように。docs/test-plan.md
 * 5.2）。そのため、ここには Bean Validation の注釈を付けず、どの項目も null を受け付ける
 *
 * @param performedOn 実施日
 * @param body ひとこと
 * @param durationMin トレーニング時間（分）。小数を見分けるため BigDecimal
 * @param visibility 公開範囲
 * @param weightUnit 重量の単位（kg・lb）
 * @param distanceUnit 距離の単位（km・mi）
 * @param exercises 種目とセット（並び順のとおり）
 */
public record WorkoutInput(
    LocalDate performedOn,
    String body,
    BigDecimal durationMin,
    String visibility,
    String weightUnit,
    String distanceUnit,
    List<Exercise> exercises) {

  /**
   * 記録の種目。
   *
   * @param exerciseId 種目
   * @param sets セット（入力した単位のまま）
   */
  public record Exercise(Long exerciseId, List<SetInput> sets) {}
}
