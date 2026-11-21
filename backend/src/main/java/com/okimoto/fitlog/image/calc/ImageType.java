package com.okimoto.fitlog.image.calc;

import java.util.Arrays;
import java.util.Optional;

/** 受け付ける画像の形式（JPEG・PNG・WebP。docs/api.md A-20）。拡張子だけ .png にした別のファイルを画像として保存・配信しないよう、形式は中身で判定する */
public enum ImageType {
  JPEG("jpg", "image/jpeg"),
  PNG("png", "image/png"),
  WEBP("webp", "image/webp");

  private final String extension;
  private final String contentType;

  ImageType(String extension, String contentType) {
    this.extension = extension;
    this.contentType = contentType;
  }

  /** 保存するファイルの拡張子 */
  public String extension() {
    return extension;
  }

  /** 保存するときに付ける形式。ブラウザが画像として表示するために使う */
  public String contentType() {
    return contentType;
  }

  /** ファイルの中身の先頭のバイト列（マジックナンバー）から形式を判定する。拡張子・送られた Content-Type は使わない。どれでもなければ空 */
  public static Optional<ImageType> detect(byte[] bytes) {
    if (startsWith(bytes, 0, JPEG_SIGNATURE)) {
      return Optional.of(JPEG);
    }
    if (startsWith(bytes, 0, PNG_SIGNATURE)) {
      return Optional.of(PNG);
    }
    // WebP は RIFF の入れ物で、8バイト目からが「WEBP」
    if (startsWith(bytes, 0, RIFF) && startsWith(bytes, 8, WEBP_SIGNATURE)) {
      return Optional.of(WEBP);
    }
    return Optional.empty();
  }

  private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
  private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
  private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
  private static final byte[] WEBP_SIGNATURE = {'W', 'E', 'B', 'P'};

  private static boolean startsWith(byte[] bytes, int offset, byte[] signature) {
    return bytes.length >= offset + signature.length
        && Arrays.equals(bytes, offset, offset + signature.length, signature, 0, signature.length);
  }
}
