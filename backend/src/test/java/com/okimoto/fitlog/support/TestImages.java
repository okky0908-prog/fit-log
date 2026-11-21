package com.okimoto.fitlog.support;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

/**
 * テスト用の小さな画像（数百バイト）。位置情報（EXIF の GPS・XMP の位置）を入れた画像も作れる。
 *
 * <p>JPEG・PNG は Java の標準機能で作る。WebP は Java で書き出せないので、cwebp で作った 2×2 の画像（{@code
 * images/plain.webp}）を使い、付属データの塊（chunk）を足す
 */
public final class TestImages {

  /** 位置情報の XMP（緯度だけ） */
  private static final String XMP_WITH_LOCATION =
      """
      <x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">\
      <rdf:Description rdf:about="" xmlns:exif="http://ns.adobe.com/exif/1.0/" exif:GPSLatitude="35,40.5N"/>\
      </rdf:RDF></x:xmpmeta>\
      """;

  /** 位置情報のない XMP（作成したソフトの名前だけ） */
  private static final String XMP_WITHOUT_LOCATION =
      """
      <x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">\
      <rdf:Description rdf:about="" xmlns:xmp="http://ns.adobe.com/xap/1.0/" xmp:CreatorTool="test"/>\
      </rdf:RDF></x:xmpmeta>\
      """;

  private TestImages() {}

  public static byte[] jpeg() {
    return write("jpg");
  }

  public static byte[] png() {
    return write("png");
  }

  public static byte[] webp() {
    try (InputStream in = TestImages.class.getResourceAsStream("/images/plain.webp")) {
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** 先頭のバイト列は GIF（形式の違反） */
  public static byte[] gif() {
    return "GIF89a\u0001\u0000\u0001\u0000".getBytes(StandardCharsets.ISO_8859_1);
  }

  /** 指定の大きさの JPEG（足りない分は、末尾に 0 を足す。形式の判定は先頭のバイト列で行うので、JPEG として扱われる） */
  public static byte[] jpegOfSize(int size) {
    return Arrays.copyOf(jpeg(), size);
  }

  // ===== JPEG の付属データ =====

  /** EXIF の GPS（緯度の南北だけ）を入れた JPEG */
  public static byte[] jpegWithGps() {
    return insertJpegSegment(jpeg(), exifApp1(tiffWithGps()));
  }

  /** EXIF に GPS の欄はあるが、項目が1つもない JPEG（位置情報を切ったカメラが書くことがある） */
  public static byte[] jpegWithEmptyGps() {
    return insertJpegSegment(jpeg(), exifApp1(tiffWithEmptyGps()));
  }

  /** 位置情報のない EXIF（画像の向きだけ）を入れた JPEG */
  public static byte[] jpegWithExifWithoutGps() {
    return insertJpegSegment(jpeg(), exifApp1(tiffWithOrientationOnly()));
  }

  /** XMP にだけ位置情報を入れた JPEG */
  public static byte[] jpegWithXmpLocation() {
    return insertJpegSegment(jpeg(), xmpApp1(XMP_WITH_LOCATION));
  }

  /** 位置情報のない XMP を入れた JPEG */
  public static byte[] jpegWithXmpWithoutLocation() {
    return insertJpegSegment(jpeg(), xmpApp1(XMP_WITHOUT_LOCATION));
  }

  // ===== PNG の付属データ =====

  /** eXIf の塊に GPS を入れた PNG */
  public static byte[] pngWithGps() {
    return insertPngChunk(png(), "eXIf", tiffWithGps());
  }

  /** XMP（iTXt の塊）に位置情報を入れた PNG */
  public static byte[] pngWithXmpLocation() {
    return insertPngChunk(png(), "iTXt", xmpItxt(XMP_WITH_LOCATION));
  }

  // ===== WebP の付属データ =====

  /** EXIF の塊に GPS を入れた WebP */
  public static byte[] webpWithGps() {
    return extendedWebp("EXIF", tiffWithGps(), 0x08);
  }

  /** XMP の塊に位置情報を入れた WebP */
  public static byte[] webpWithXmpLocation() {
    return extendedWebp("XMP ", XMP_WITH_LOCATION.getBytes(StandardCharsets.UTF_8), 0x04);
  }

  // ===== 組み立て =====

  private static byte[] write(String format) {
    BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
    image.setRGB(0, 0, 0xC83C3C);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      ImageIO.write(image, format, out);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toByteArray();
  }

  /** TIFF（EXIF の中身）。IFD0 に GPS の IFD への参照を1つ置き、GPS の IFD に緯度の南北（N）を1つ置く */
  private static byte[] tiffWithGps() {
    ByteBuffer tiff = tiffHeader(44);
    // IFD0（8バイト目から）：項目1つ
    tiff.putShort((short) 1);
    ifdEntry(tiff, 0x8825, 4, 1, 26); // GPSInfo（LONG）→ 26バイト目の IFD
    tiff.putInt(0); // 次の IFD はない
    // GPS の IFD（26バイト目から）：項目1つ
    tiff.putShort((short) 1);
    tiff.putShort((short) 0x0001).putShort((short) 2).putInt(2); // GPSLatitudeRef（ASCII, 2文字）
    tiff.put(new byte[] {'N', 0, 0, 0});
    tiff.putInt(0);
    return tiff.array();
  }

  /** TIFF（EXIF の中身）。GPS の IFD への参照はあるが、GPS の IFD の項目は0個 */
  private static byte[] tiffWithEmptyGps() {
    ByteBuffer tiff = tiffHeader(32);
    tiff.putShort((short) 1);
    ifdEntry(tiff, 0x8825, 4, 1, 26);
    tiff.putInt(0);
    tiff.putShort((short) 0);
    tiff.putInt(0);
    return tiff.array();
  }

  /** TIFF（EXIF の中身）。IFD0 に画像の向き（Orientation）だけを置く */
  private static byte[] tiffWithOrientationOnly() {
    ByteBuffer tiff = tiffHeader(26);
    tiff.putShort((short) 1);
    tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1); // Orientation（SHORT）
    tiff.putShort((short) 1).putShort((short) 0);
    tiff.putInt(0);
    return tiff.array();
  }

  private static ByteBuffer tiffHeader(int size) {
    ByteBuffer tiff = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    tiff.put(new byte[] {'I', 'I'}).putShort((short) 42).putInt(8);
    return tiff;
  }

  private static void ifdEntry(ByteBuffer tiff, int tag, int type, int count, int value) {
    tiff.putShort((short) tag).putShort((short) type).putInt(count).putInt(value);
  }

  private static byte[] exifApp1(byte[] tiff) {
    return concat("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1), tiff);
  }

  private static byte[] xmpApp1(String xmp) {
    return concat(
        "http://ns.adobe.com/xap/1.0/\0".getBytes(StandardCharsets.ISO_8859_1),
        xmp.getBytes(StandardCharsets.UTF_8));
  }

  /** JPEG の先頭（SOI）の直後に、APP1 の区切りを入れる */
  private static byte[] insertJpegSegment(byte[] jpeg, byte[] app1Payload) {
    int length = app1Payload.length + 2;
    byte[] marker = {(byte) 0xFF, (byte) 0xE1, (byte) (length >> 8), (byte) length};
    return concat(
        Arrays.copyOf(jpeg, 2), marker, app1Payload, Arrays.copyOfRange(jpeg, 2, jpeg.length));
  }

  private static byte[] xmpItxt(String xmp) {
    // キーワード、区切り、圧縮なし、圧縮方式、言語（空）、区切り、翻訳したキーワード（空）、区切り、本文
    return concat(
        "XML:com.adobe.xmp".getBytes(StandardCharsets.ISO_8859_1),
        new byte[] {0, 0, 0, 0, 0},
        xmp.getBytes(StandardCharsets.UTF_8));
  }

  /** PNG の IHDR（8 + 25 バイト）の直後に、塊を1つ入れる */
  private static byte[] insertPngChunk(byte[] png, String type, byte[] data) {
    byte[] typeBytes = type.getBytes(StandardCharsets.ISO_8859_1);
    CRC32 crc = new CRC32();
    crc.update(typeBytes);
    crc.update(data);
    ByteBuffer chunk = ByteBuffer.allocate(12 + data.length);
    chunk.putInt(data.length).put(typeBytes).put(data).putInt((int) crc.getValue());
    int afterIhdr = 8 + 25;
    return concat(
        Arrays.copyOf(png, afterIhdr),
        chunk.array(),
        Arrays.copyOfRange(png, afterIhdr, png.length));
  }

  /**
   * 拡張形式の WebP（VP8X の塊＋画像の塊＋付属データの塊）。
   *
   * @param flag VP8X の、付属データがあることを示す印（EXIF は 0x08、XMP は 0x04）
   */
  private static byte[] extendedWebp(String chunkType, byte[] data, int flag) {
    byte[] plain = webp();
    byte[] imageChunk = Arrays.copyOfRange(plain, 12, plain.length);
    ByteBuffer vp8x = ByteBuffer.allocate(18).order(ByteOrder.LITTLE_ENDIAN);
    vp8x.put("VP8X".getBytes(StandardCharsets.ISO_8859_1)).putInt(10);
    vp8x.put((byte) flag).put(new byte[3]);
    // 幅と高さ（それぞれ 1 を引いた値を 3バイトで）。2×2
    vp8x.put(new byte[] {1, 0, 0, 1, 0, 0});
    int padded = data.length + (data.length % 2);
    ByteBuffer meta = ByteBuffer.allocate(8 + padded).order(ByteOrder.LITTLE_ENDIAN);
    meta.put(chunkType.getBytes(StandardCharsets.ISO_8859_1)).putInt(data.length).put(data);
    byte[] body =
        concat(
            "WEBP".getBytes(StandardCharsets.ISO_8859_1), vp8x.array(), imageChunk, meta.array());
    ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
    header.put("RIFF".getBytes(StandardCharsets.ISO_8859_1)).putInt(body.length);
    return concat(header.array(), body);
  }

  private static byte[] concat(byte[]... parts) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (byte[] part : parts) {
      out.writeBytes(part);
    }
    return out.toByteArray();
  }
}
