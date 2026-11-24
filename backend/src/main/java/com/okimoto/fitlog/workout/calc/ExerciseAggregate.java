package com.okimoto.fitlog.workout.calc;

import java.math.BigDecimal;

/**
 * 一覧の種目の要約のもとになる、種目ごとにセットを SQL で集計した値（docs/database.md「N+1 問題を防ぐ」）。
 *
 * @param exerciseId 種目
 * @param name 種目の名前
 * @param recordType 記録方式（weight_reps・reps・time_distance）
 * @param setCount セットの数
 * @param uniform 全セットが同じ重量・回数か（時間・距離の種目では使わない）
 * @param bestWeightKg 最大のセットの重量（回数が1回以上のセットの中で一番重い。同じなら回数が多い・並び順が先。全セット0回なら一番重い）
 * @param bestReps 最大のセットの回数（回数のみの種目では、一番多い回数）
 * @param totalReps 回数の合計
 * @param totalDurationSec 時間の合計（秒）
 * @param totalDistanceKm 距離の合計（km）。どのセットにも距離がなければ null
 */
public record ExerciseAggregate(
    long exerciseId,
    String name,
    String recordType,
    int setCount,
    boolean uniform,
    BigDecimal bestWeightKg,
    Integer bestReps,
    Integer totalReps,
    Integer totalDurationSec,
    BigDecimal totalDistanceKm) {}
