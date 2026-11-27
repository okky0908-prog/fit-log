package com.okimoto.fitlog.stats.calc;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 種目ごとの推移の、実施日ごとの値（SQL が返すもの。記録方式に関係なく、すべての列を入れる）。
 *
 * @param date 実施日
 * @param workoutId その日の一番よい値を出した記録
 * @param maxWeightKg 回数が1回以上のセットの最大重量。なければ null
 * @param maxE1rmKg 回数が1〜10回のセットの最大の推定 1RM（小数第3位に四捨五入）。なければ null
 * @param maxWeightReps 最大重量のセットの回数（同じ重量なら多い方）。なければ null
 * @param totalReps 回数の合計。なければ null
 * @param maxReps 1セットの最大回数。なければ null
 * @param totalDurationSec 時間の合計。なければ null
 * @param totalDistanceKm 距離の合計。距離のあるセットがなければ null
 * @param pacedDurationSec 距離のあるセットの時間の合計（ペースに使う）。なければ 0
 */
public record DayAggregate(
    LocalDate date,
    long workoutId,
    BigDecimal maxWeightKg,
    BigDecimal maxE1rmKg,
    Integer maxWeightReps,
    Integer totalReps,
    Integer maxReps,
    Integer totalDurationSec,
    BigDecimal totalDistanceKm,
    int pacedDurationSec) {}
