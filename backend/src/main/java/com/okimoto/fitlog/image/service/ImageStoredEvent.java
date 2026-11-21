package com.okimoto.fitlog.image.service;

/**
 * 画像を S3 に保存した（DB の保存はまだ確定していない）。
 *
 * @param key S3 のキー
 */
public record ImageStoredEvent(String key) {}
