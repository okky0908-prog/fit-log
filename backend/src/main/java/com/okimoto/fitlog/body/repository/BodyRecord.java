package com.okimoto.fitlog.body.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 体重の記録の1件。
 *
 * @param recordedOn 日付
 * @param weightKg 体重（kg、小数第3位まで）
 * @param bodyFatPct 体脂肪率（%）。なければ null
 */
public record BodyRecord(LocalDate recordedOn, BigDecimal weightKg, BigDecimal bodyFatPct) {}
