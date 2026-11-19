package com.okimoto.fitlog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * アプリのクラスが、どれかの層のパッケージに入っていることを確かめる（docs/test-plan.md 6章）。
 *
 * <p>カバレッジの基準（JaCoCo）は、クラスの名前のパターン（{@code *.calc.*}・{@code *.service.*}）で層ごとに分けている。
 * 層の外に置いたクラスは基準の対象から漏れるので、ここで止める。
 *
 * <p>層を足すときは、この正規表現とテスト計画書 6章の表を同じ PR で直す。
 */
class LayerPackageTest {

  /**
   * {@code com.okimoto.fitlog.<機能>.<層>.<クラス>}（内部クラスの {@code $…} を含む）。{@code config} は Spring
   * の設定・フィルター・設定値（分岐の基準はなく、結合テストで確かめる）。 機能をまたいで使う部品は、機能の名前を {@code common} にする
   */
  private static final Pattern LAYERED =
      Pattern.compile(
          "com\\.okimoto\\.fitlog\\.[a-z]+\\.(controller|service|calc|repository|config)\\.[^.]+");

  /** 層に入れないクラス：アプリの入口と、jOOQ が生成したコード（テストの対象にしない） */
  private static final Pattern EXCLUDED =
      Pattern.compile(
          "com\\.okimoto\\.fitlog\\.FitLogApplication|com\\.okimoto\\.fitlog\\.jooq\\..+");

  @Test
  void アプリのクラスはどれかの層のパッケージに入っている() throws IOException, URISyntaxException {
    assertThat(mainClassNames())
        .as("アプリのクラス")
        .isNotEmpty()
        .filteredOn(name -> !EXCLUDED.matcher(name).matches())
        .as("層（<機能>.controller / service / calc / repository / config）の外に置いたクラス")
        .allMatch(name -> LAYERED.matcher(name).matches());
  }

  /** アプリ（src/main）のクラスの名前。テストのクラスは含めない */
  private static List<String> mainClassNames() throws IOException, URISyntaxException {
    Path root =
        Path.of(
            FitLogApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    try (Stream<Path> files = Files.walk(root)) {
      return files
          .filter(file -> file.toString().endsWith(".class"))
          .map(file -> root.relativize(file).toString())
          .map(path -> path.substring(0, path.length() - ".class".length()).replace('/', '.'))
          .toList();
    }
  }
}
