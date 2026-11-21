package com.okimoto.fitlog.image.calc;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.xmp.XmpDirectory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;

/** 記録の画像の決まり（docs/api.md A-20） */
public final class ImageRules {

  /** 1枚の大きさの上限（2MB） */
  public static final int MAX_BYTES = 2 * 1024 * 1024;

  /** 1つの記録の画像の上限 */
  public static final int MAX_COUNT = 4;

  public static final String TYPE_MESSAGE = "JPEG・PNG・WebP の画像を選んでください";
  public static final String SIZE_MESSAGE = "画像は1枚2MBまでです";
  public static final String LOCATION_MESSAGE = "位置情報を含む画像はアップロードできません";
  public static final String COUNT_MESSAGE = "画像は4枚まで選べます";

  /** XMP の位置の項目（exif:GPSLatitude など）の名前の始まり */
  private static final String XMP_LOCATION_PREFIX = "exif:GPS";

  private ImageRules() {}

  /**
   * 1枚の画像の問題。大きさ → 形式 → 位置情報の順に確かめる。
   *
   * @return 画面に出す文言。問題がなければ空
   */
  public static Optional<String> problem(byte[] bytes) {
    if (bytes.length > MAX_BYTES) {
      return Optional.of(SIZE_MESSAGE);
    }
    if (ImageType.detect(bytes).isEmpty()) {
      return Optional.of(TYPE_MESSAGE);
    }
    Metadata metadata;
    try {
      metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes), bytes.length);
    } catch (ImageProcessingException | IOException e) {
      // 先頭のバイト列だけ画像で、中身が読めない
      return Optional.of(TYPE_MESSAGE);
    }
    return hasLocation(metadata) ? Optional.of(LOCATION_MESSAGE) : Optional.empty();
  }

  /** EXIF の GPS の項目か、XMP の位置の項目が1つでもあるか。位置情報以外の EXIF（撮影日時・機種など）は見ない */
  private static boolean hasLocation(Metadata metadata) {
    for (GpsDirectory gps : metadata.getDirectoriesOfType(GpsDirectory.class)) {
      if (gps.getTagCount() > 0) {
        return true;
      }
    }
    for (XmpDirectory xmp : metadata.getDirectoriesOfType(XmpDirectory.class)) {
      if (xmp.getXmpProperties().keySet().stream()
          .anyMatch(name -> name.startsWith(XMP_LOCATION_PREFIX))) {
        return true;
      }
    }
    return false;
  }
}
