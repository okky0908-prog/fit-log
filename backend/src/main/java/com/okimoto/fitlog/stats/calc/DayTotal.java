package com.okimoto.fitlog.stats.calc;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 1日分の、本人の記録の合計（記録のある日だけ SQL で求める）。
 *
 * @param date 実施日
 * @param volumeKg 総挙上量（重量 × 回数のセットの、重量 × 回数の合計。kg）
 * @param durationSec 運動時間（時間・距離のセットの時間の合計。秒）
 * @param distanceKm 距離（時間・距離のセットの距離の合計。km）
 */
public record DayTotal(
    LocalDate date, BigDecimal volumeKg, int durationSec, BigDecimal distanceKm) {}
