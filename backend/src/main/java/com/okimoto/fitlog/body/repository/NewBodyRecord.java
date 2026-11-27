package com.okimoto.fitlog.body.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 保存する体重の記録。
 *
 * @param userId 記録する人
 * @param recordedOn 日付
 * @param weightKg 体重（kg、小数第3位まで）
 * @param bodyFatPct 体脂肪率（%）。なければ null
 */
public record NewBodyRecord(
    long userId, LocalDate recordedOn, BigDecimal weightKg, BigDecimal bodyFatPct) {}
