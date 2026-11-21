package com.okimoto.fitlog.image.calc;

import static org.assertj.core.api.Assertions.assertThat;

import com.okimoto.fitlog.support.TestImages;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** 記録の画像の決まり（docs/api.md A-20、docs/test-plan.md 5.1 の画像の行） */
class ImageRulesTest {

  private static final String TYPE = "JPEG・PNG・WebP の画像を選んでください";
  private static final String SIZE = "画像は1枚2MBまでです";
  private static final String LOCATION = "位置情報を含む画像はアップロードできません";

  @Test
  void 形式はファイルの中身の先頭のバイト列で判定する() {
    assertThat(ImageType.detect(TestImages.jpeg())).contains(ImageType.JPEG);
    assertThat(ImageType.detect(TestImages.png())).contains(ImageType.PNG);
    assertThat(ImageType.detect(TestImages.webp())).contains(ImageType.WEBP);
    assertThat(ImageType.detect(TestImages.gif())).isEmpty();
    // RIFF だが WebP ではない（WAV の音声）
    assertThat(ImageType.detect(bytes("RIFF\0\0\0\0WAVEfmt "))).isEmpty();
    // 先頭のバイト列より短い
    assertThat(ImageType.detect(bytes("RIFF"))).isEmpty();
    assertThat(ImageType.detect(new byte[0])).isEmpty();
  }

  @Test
  void 保存する拡張子とContentTypeは形式で決まる() {
    assertThat(ImageType.JPEG.extension()).isEqualTo("jpg");
    assertThat(ImageType.JPEG.contentType()).isEqualTo("image/jpeg");
    assertThat(ImageType.PNG.extension()).isEqualTo("png");
    assertThat(ImageType.PNG.contentType()).isEqualTo("image/png");
    assertThat(ImageType.WEBP.extension()).isEqualTo("webp");
    assertThat(ImageType.WEBP.contentType()).isEqualTo("image/webp");
  }

  @Test
  void JPEGとPNGとWebPは受け付ける() {
    assertThat(ImageRules.problem(TestImages.jpeg())).isEmpty();
    assertThat(ImageRules.problem(TestImages.png())).isEmpty();
    assertThat(ImageRules.problem(TestImages.webp())).isEmpty();
  }

  @Test
  void それ以外の形式は受け付けない_拡張子ではなく中身で判定する() {
    assertThat(ImageRules.problem(TestImages.gif())).contains(TYPE);
  }

  @Test
  void 大きさは2MBちょうどまで() {
    assertThat(ImageRules.problem(TestImages.jpegOfSize(2 * 1024 * 1024))).isEmpty();
    assertThat(ImageRules.problem(TestImages.jpegOfSize(2 * 1024 * 1024 + 1))).contains(SIZE);
  }

  @Test
  void 大きすぎる画像は_形式より先に大きさを返す() {
    byte[] bigGif = Arrays.copyOf(TestImages.gif(), 2 * 1024 * 1024 + 1);

    assertThat(ImageRules.problem(bigGif)).contains(SIZE);
  }

  @Test
  void EXIFのGPSがあれば受け付けない_JPEG_PNG_WebPのどれも() {
    assertThat(ImageRules.problem(TestImages.jpegWithGps())).contains(LOCATION);
    assertThat(ImageRules.problem(TestImages.pngWithGps())).contains(LOCATION);
    assertThat(ImageRules.problem(TestImages.webpWithGps())).contains(LOCATION);
  }

  @Test
  void XMPにだけ位置情報があっても受け付けない_JPEG_PNG_WebPのどれも() {
    assertThat(ImageRules.problem(TestImages.jpegWithXmpLocation())).contains(LOCATION);
    assertThat(ImageRules.problem(TestImages.pngWithXmpLocation())).contains(LOCATION);
    assertThat(ImageRules.problem(TestImages.webpWithXmpLocation())).contains(LOCATION);
  }

  @Test
  void 位置情報のないEXIFとXMPは受け付ける() {
    assertThat(ImageRules.problem(TestImages.jpegWithExifWithoutGps())).isEmpty();
    assertThat(ImageRules.problem(TestImages.jpegWithEmptyGps())).as("GPS の欄が空").isEmpty();
    assertThat(ImageRules.problem(TestImages.jpegWithXmpWithoutLocation())).isEmpty();
  }

  @Test
  void 先頭だけ画像で中身が読めないものは_形式の違反() {
    // PNG の先頭のバイト列のあとが壊れている
    byte[] broken = Arrays.copyOf(TestImages.png(), 12);

    assertThat(ImageRules.problem(broken)).contains(TYPE);
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.ISO_8859_1);
  }
}
