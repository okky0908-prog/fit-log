package com.okimoto.fitlog.stats.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 全期間の自己ベスト・推定 1RM の最高（A-96）。
 *
 * @param weightKg 重量（推定 1RM なら、小数第3位に四捨五入した値）
 * @param performedOn その値を最初に出した記録の実施日
 * @param workoutId その記録
 */
public record BestWeight(BigDecimal weightKg, LocalDate performedOn, long workoutId) {}
