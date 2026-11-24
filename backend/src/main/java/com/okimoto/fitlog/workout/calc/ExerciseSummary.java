package com.okimoto.fitlog.workout.calc;

import java.math.BigDecimal;

/**
 * 一覧の種目の要約（docs/api.md「WorkoutCard」の exerciseSummaries の1件）。使わない項目は null。
 *
 * @param exerciseId 種目
 * @param name 種目の名前
 * @param recordType 記録方式
 * @param personalBest 自己ベストを更新したか（段階 2 で作る。今は false）
 * @param setCount セットの数
 * @param uniform 全セットが同じ値か（時間・距離の種目では null）
 * @param weightKg 重量 × 回数：全セットが同じならその値、違えば最大のセットの値
 * @param reps 重量 × 回数：weightKg と同じセットの回数。回数のみ：全セットが同じときの回数
 * @param totalReps 回数のみで、値が違うときの回数の合計
 * @param durationSec 時間・距離：時間（2セット以上なら合計）
 * @param distanceKm 時間・距離：距離（2セット以上なら合計）。距離がなければ null
 * @param paceSecPerKm 時間・距離：1セットで距離があるときだけ、1km あたりの秒数
 */
public record ExerciseSummary(
    long exerciseId,
    String name,
    String recordType,
    boolean personalBest,
    int setCount,
    Boolean uniform,
    BigDecimal weightKg,
    Integer reps,
    Integer totalReps,
    Integer durationSec,
    BigDecimal distanceKm,
    Integer paceSecPerKm) {}
