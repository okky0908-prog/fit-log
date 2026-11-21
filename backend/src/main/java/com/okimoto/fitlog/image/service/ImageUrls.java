package com.okimoto.fitlog.image.service;

import com.okimoto.fitlog.image.config.StorageProperties;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4FamilyHttpSigner.AuthLocation;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.HttpSigner;
import software.amazon.awssdk.identity.spi.AwsCredentialsIdentity;

/**
 * 画像の署名付き URL（docs/tech-stack.md 3.9）。閲覧を許可したあとにだけ作る（S3 のバケットは公開しない）。
 *
 * <ul>
 *   <li>署名の時刻を「1時間ごとの区切り」（毎時0分0秒）にそろえる。同じ1時間の中では同じ URL になり、ブラウザのキャッシュが効く
 *   <li>期限は署名の時刻から2時間。どの時刻に作っても、1時間以上は使える
 *   <li>署名の計算はサーバーの中だけで済む（S3 と通信しない）ので、一覧で多く作っても速い
 * </ul>
 *
 * S3Presigner は署名の時刻を指定できないので、AWS SDK の署名の部品（{@link AwsV4HttpSigner}）を直接使う
 */
@Component
public class ImageUrls {

  /** 署名の時刻からの期限 */
  static final Duration EXPIRATION = Duration.ofHours(2);

  private final StorageProperties properties;
  private final AwsCredentialsProvider credentials;
  private final Clock clock;
  private final AwsV4HttpSigner signer = AwsV4HttpSigner.create();

  public ImageUrls(StorageProperties properties, AwsCredentialsProvider credentials, Clock clock) {
    this.properties = properties;
    this.credentials = credentials;
    this.clock = clock;
  }

  /** そのキーの画像を見るための URL（GET だけに使える） */
  public String urlOf(String key) {
    SdkHttpRequest request =
        SdkHttpRequest.builder().method(SdkHttpMethod.GET).uri(objectUri(key)).build();
    Clock signedAt = Clock.fixed(clock.instant().truncatedTo(ChronoUnit.HOURS), ZoneOffset.UTC);
    return signer
        .sign(r ->
            r.identity((AwsCredentialsIdentity) credentials.resolveCredentials())
                .request(request)
                .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, "s3")
                .putProperty(AwsV4HttpSigner.REGION_NAME, properties.region())
                .putProperty(AwsV4HttpSigner.AUTH_LOCATION, AuthLocation.QUERY_STRING)
                .putProperty(AwsV4HttpSigner.EXPIRATION_DURATION, EXPIRATION)
                // S3 の決まり：中身は署名しない（UNSIGNED-PAYLOAD）・パスを二重に符号化しない・正規化しない
                .putProperty(AwsV4HttpSigner.PAYLOAD_SIGNING_ENABLED, false)
                .putProperty(AwsV4HttpSigner.DOUBLE_URL_ENCODE, false)
                .putProperty(AwsV4HttpSigner.NORMALIZE_PATH, false)
                .putProperty(HttpSigner.SIGNING_CLOCK, signedAt))
        .request()
        .getUri()
        .toString();
  }

  /** LocalStack はバケットをパスに入れる形、AWS の S3 はバケットをホスト名に入れる形 */
  private URI objectUri(String key) {
    if (properties.usesCustomEndpoint()) {
      String endpoint = properties.endpoint().replaceAll("/+$", "");
      return URI.create(endpoint + "/" + properties.bucket() + "/" + key);
    }
    return URI.create(
        "https://" + properties.bucket() + ".s3." + properties.region() + ".amazonaws.com/" + key);
  }
}
