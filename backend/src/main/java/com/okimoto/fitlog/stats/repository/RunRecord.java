package com.okimoto.fitlog.stats.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 1回の走行（ランニングのセット1つ）。最速ペース・最長距離に使う。
 *
 * @param workoutId 記録（押すと詳細を開く）
 * @param performedOn 実施日
 * @param distanceKm 距離（km）
 * @param durationSec 時間（秒）
 */
public record RunRecord(
    long workoutId, LocalDate performedOn, BigDecimal distanceKm, int durationSec) {}
