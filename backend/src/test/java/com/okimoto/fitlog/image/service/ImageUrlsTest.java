package com.okimoto.fitlog.image.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.okimoto.fitlog.image.config.StorageProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

/** 画像の署名付き URL（docs/tech-stack.md 3.9、docs/test-plan.md 4.4 の「署名付き URL」）。署名はサーバーの中だけで計算する（通信しない） */
class ImageUrlsTest {

  private static final String KEY = "workouts/2026/11/0b4f.jpg";
  private static final StorageProperties LOCALSTACK =
      new StorageProperties("fitlog-images", "ap-northeast-1", "http://localhost:4566");
  private static final StorageProperties AWS =
      new StorageProperties("fitlog-images", "ap-northeast-1", "");

  @Test
  void 同じ1時間の中では同じURL_時の区切りを越えると変わる() {
    String at1000 = urls(LOCALSTACK, "2026-11-21T01:00:00Z").urlOf(KEY);
    String at1059 = urls(LOCALSTACK, "2026-11-21T01:59:59Z").urlOf(KEY);
    String at1100 = urls(LOCALSTACK, "2026-11-21T02:00:00Z").urlOf(KEY);

    assertThat(at1059).isEqualTo(at1000);
    assertThat(at1100).isNotEqualTo(at1059);
  }

  @Test
  void 署名の時刻は時の区切りで_期限は2時間_どの時刻に作っても1時間以上使える() {
    String url = urls(LOCALSTACK, "2026-11-21T01:59:59Z").urlOf(KEY);

    assertThat(url)
        .contains("X-Amz-Date=20261121T010000Z")
        .contains("X-Amz-Expires=7200")
        .contains("X-Amz-Signature=")
        .contains("X-Amz-Algorithm=AWS4-HMAC-SHA256");
  }

  @Test
  void キーごとに違うURL() {
    ImageUrls urls = urls(LOCALSTACK, "2026-11-21T01:00:00Z");

    assertThat(urls.urlOf("workouts/2026/11/a.jpg"))
        .isNotEqualTo(urls.urlOf("workouts/2026/11/b.jpg"));
  }

  @Test
  void LocalStackではバケットをパスに入れ_AWSではバケットをホスト名に入れる() {
    assertThat(urls(LOCALSTACK, "2026-11-21T01:00:00Z").urlOf(KEY))
        .startsWith("http://localhost:4566/fitlog-images/workouts/2026/11/0b4f.jpg?");
    assertThat(urls(AWS, "2026-11-21T01:00:00Z").urlOf(KEY))
        .startsWith(
            "https://fitlog-images.s3.ap-northeast-1.amazonaws.com/workouts/2026/11/0b4f.jpg?");
  }

  private static ImageUrls urls(StorageProperties properties, String now) {
    return new ImageUrls(
        properties,
        StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")),
        Clock.fixed(Instant.parse(now), ZoneOffset.UTC));
  }
}
