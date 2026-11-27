package com.okimoto.fitlog.stats.repository;

/**
 * 部位別のセット数。
 *
 * @param bodyPart 部位（chest など）
 * @param setCount セットの数
 */
public record BodyPartCount(String bodyPart, int setCount) {}
