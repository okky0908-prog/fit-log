package com.okimoto.fitlog.image.service;

/**
 * 画像を使わなくなった（編集で外した・記録を削除した。DB の保存はまだ確定していない）。
 *
 * @param key S3 のキー
 */
public record ImageDiscardedEvent(String key) {}
