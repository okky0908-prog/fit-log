package com.okimoto.fitlog.image.config;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * S3 への接続（前回の SNS アプリと同じ）。
 *
 * <ul>
 *   <li>接続先の URL があるとき（手元の開発・テスト）：LocalStack に接続する。LocalStack は認証情報を確かめないので、ダミーの値を使う。URL
 *       はバケットをパスに入れる形（http://localhost:4566/バケット/キー）にする
 *   <li>接続先の URL が空のとき（本番）：AWS の S3 に接続する。認証情報は IAM ロールから自動で取る（アクセスキーをサーバーに置かない）
 * </ul>
 *
 * 認証情報は、署名付き URL を作るとき（{@code ImageUrls}）にも使う
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class S3Config {

  @Bean
  AwsCredentialsProvider awsCredentialsProvider(StorageProperties properties) {
    if (properties.usesCustomEndpoint()) {
      return StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));
    }
    return DefaultCredentialsProvider.builder().build();
  }

  @Bean
  S3Client s3Client(StorageProperties properties, AwsCredentialsProvider credentials) {
    var builder =
        S3Client.builder().region(Region.of(properties.region())).credentialsProvider(credentials);
    if (properties.usesCustomEndpoint()) {
      builder.endpointOverride(URI.create(properties.endpoint())).forcePathStyle(true);
    }
    return builder.build();
  }
}
