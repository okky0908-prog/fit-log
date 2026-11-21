package com.okimoto.fitlog.exercise.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 新しく保存する自分専用の種目。
 *
 * @param ownerId 作った人
 * @param name 名前（前後の空白を除いたもの）
 * @param nameKey 同じ名前かどうかの判定と検索に使う名前（ExerciseRules.nameKey）
 * @param bodyPart 部位
 * @param recordType 記録方式
 * @param mets 標準の METs
 * @param createdAt 作った日時
 */
public record NewExercise(
    long ownerId,
    String name,
    String nameKey,
    String bodyPart,
    String recordType,
    BigDecimal mets,
    OffsetDateTime createdAt) {}
