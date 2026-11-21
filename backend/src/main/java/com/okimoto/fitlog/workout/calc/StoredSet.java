package com.okimoto.fitlog.workout.calc;

import java.math.BigDecimal;

/**
 * 保存するセット（kg・km・秒）。記録方式で使わない項目は null。
 *
 * @param weightKg 重量（kg、小数第3位まで）
 * @param reps 回数
 * @param durationSec 時間（秒）
 * @param distanceKm 距離（km、小数第3位まで）
 */
public record StoredSet(
    BigDecimal weightKg, Integer reps, Integer durationSec, BigDecimal distanceKm) {}
