package com.okimoto.fitlog.workout.calc;

import java.math.BigDecimal;

/**
 * 自己ベストを更新した、記録の種目（docs/database.md「自己ベストと『自己ベスト更新』の印」の SQL の結果）。
 *
 * @param workoutId 記録
 * @param exerciseId 種目
 * @param maxWeightKg その記録のその種目の最大重量（回数が1回以上のセットの中で）。前の記録までの自己ベストを超えている
 */
public record PersonalBest(long workoutId, long exerciseId, BigDecimal maxWeightKg) {}
