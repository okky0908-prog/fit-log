package com.okimoto.fitlog.image.service;

import com.okimoto.fitlog.common.calc.JapanTime;
import com.okimoto.fitlog.common.controller.ApiException;
import com.okimoto.fitlog.common.controller.ErrorCode;
import com.okimoto.fitlog.image.calc.ImageType;
import com.okimoto.fitlog.image.config.StorageProperties;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 画像の S3 への保存と削除（docs/api.md A-20）。DB には S3 のキーだけを保存する。
 *
 * <p>画像は DB より先に S3 に保存する。保存したら {@link ImageStoredEvent} を出し、DB の保存が取り消されたときに {@link
 * ImageCleanupListener} が消す（どこからも使われない画像を残さない）
 */
@Component
public class ImageStorage {

  private static final Logger log = LoggerFactory.getLogger(ImageStorage.class);

  private final S3Client s3Client;
  private final StorageProperties properties;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public ImageStorage(
      S3Client s3Client,
      StorageProperties properties,
      ApplicationEventPublisher events,
      Clock clock) {
    this.s3Client = s3Client;
    this.properties = properties;
    this.events = events;
    this.clock = clock;
  }

  /**
   * 記録の画像を {@code workouts/{年}/{月}/{UUID}.{拡張子}}（年と月は日本時間）に保存し、キーを返す。元のファイル名は使わない。
   *
   * <p>トランザクションの中で呼ぶ。保存できなければ 500（IMAGE_UPLOAD_FAILED）
   */
  public String storeWorkoutImage(byte[] bytes, ImageType type) {
    LocalDate today = JapanTime.today(clock);
    String key =
        "workouts/%d/%02d/%s.%s"
            .formatted(today.getYear(), today.getMonthValue(), UUID.randomUUID(), type.extension());
    try {
      s3Client.putObject(
          r -> r.bucket(properties.bucket()).key(key).contentType(type.contentType()),
          RequestBody.fromBytes(bytes));
    } catch (SdkException e) {
      log.error("画像を保存できませんでした: {}", key, e);
      throw new ApiException(ErrorCode.IMAGE_UPLOAD_FAILED);
    }
    events.publishEvent(new ImageStoredEvent(key));
    return key;
  }

  /** 使わなくなった画像（編集で外した・記録を削除した）を、保存の確定後に消す。トランザクションの中で呼ぶ。取り消されたら消さない */
  public void discardAfterCommit(String key) {
    events.publishEvent(new ImageDiscardedEvent(key));
  }

  /** 画像を消す。失敗しても、もとの操作は成功しているので、ログに残すだけにする */
  public void deleteQuietly(String key) {
    try {
      s3Client.deleteObject(r -> r.bucket(properties.bucket()).key(key));
    } catch (SdkException e) {
      log.warn("画像を消せませんでした（S3 に残っています）: {}", key, e);
    }
  }
}
