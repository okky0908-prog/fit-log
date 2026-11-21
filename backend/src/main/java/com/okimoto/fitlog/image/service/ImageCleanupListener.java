package com.okimoto.fitlog.image.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 使われなくなった画像を S3 から消す（docs/tech-stack.md 3.6）。
 *
 * <ul>
 *   <li>保存したあとに DB の保存が取り消されたら（2枚目の画像の保存の失敗・DB の失敗など）、保存した画像を消す
 *   <li>編集で外した画像・削除した記録の画像は、保存が確定したら消す（取り消されたら、画像はまだ使われているので消さない）
 *   <li>保存の確定・取り消しのあとに、別のスレッドで動く（{@code @Async}）。S3 の応答を、API の応答に待たせない
 * </ul>
 */
@Component
public class ImageCleanupListener {

  private final ImageStorage imageStorage;

  public ImageCleanupListener(ImageStorage imageStorage) {
    this.imageStorage = imageStorage;
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
  public void onRolledBack(ImageStoredEvent event) {
    imageStorage.deleteQuietly(event.key());
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCommitted(ImageDiscardedEvent event) {
    imageStorage.deleteQuietly(event.key());
  }
}
