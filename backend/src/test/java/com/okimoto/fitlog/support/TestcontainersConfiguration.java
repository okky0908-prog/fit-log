package com.okimoto.fitlog.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * テスト用の PostgreSQL（pg_bigm 入りの自作イメージ）・Mailpit（メール）・LocalStack（S3）を、Testcontainers の使い捨てのコンテナで立ち上げる。
 *
 * <p>{@code @SpringBootTest} のテストは、すべて {@code @Import(TestcontainersConfiguration.class)}
 * を付ける（CLAUDE.md）。付け忘れると、application-test.yml の「つながらない宛先」に接続しようとして失敗する（開発用の DB には書き込まない）。
 *
 * <ul>
 *   <li>イメージは db/Dockerfile から作る（jOOQ のコード生成と同じイメージ）。2回目からは Docker のキャッシュで速く終わる
 *   <li>コンテナはテストの実行全体（JVM）で1回だけ立ち上げて使い回す。Red → Green を1件ずつ回すときの待ち時間を短くするため（docs/test-plan.md
 *       8.1）。テストが終わると Testcontainers が自動で消す
 *   <li>H2 は使わない。PostgreSQL 特有の書き方（pg_bigm・NORMALIZE など）を本番と同じ動きで確かめるため
 *   <li>Mailpit は、送ったメールを API で確かめるために使う（{@link MailpitClient}）
 *   <li>LocalStack は、記録の画像の保存先（S3）。版は docker-compose と同じ 4.14.0（それより新しい版は、起動に LocalStack
 *       のアカウントの認証トークンが要る）。バケットは起動したときに作る
 * </ul>
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  /**
   * テスト用の PostgreSQL。テストでは設定の組み合わせごとに Spring のアプリを起動して残しておき、それぞれが接続を持つので、接続の上限を既定の 100
   * から上げる（application-test.yml で、1つのアプリの接続も5本までにしている）
   */
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(fitLogPostgresImage())
          .withDatabaseName("fitlog")
          .withCommand("postgres", "-c", "max_connections=200");

  /** Mailpit の SMTP（1025）と、届いたメールを見る API（8025） */
  private static final int MAILPIT_SMTP_PORT = 1025;

  private static final int MAILPIT_API_PORT = 8025;

  private static final GenericContainer<?> MAILPIT =
      new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.33.1"))
          .withExposedPorts(MAILPIT_SMTP_PORT, MAILPIT_API_PORT);

  /** 画像を保存するバケット（application.yml の既定値と分けて、手元の開発のバケットと取り違えないようにする） */
  public static final String BUCKET = "fitlog-test-images";

  private static final LocalStackContainer LOCALSTACK =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.14.0"))
          .withServices("s3")
          // 署名付き URL の署名と期限を確かめる（LocalStack の既定では確かめない）
          .withEnv("S3_SKIP_SIGNATURE_VALIDATION", "0");

  static {
    POSTGRES.start();
    MAILPIT.start();
    LOCALSTACK.start();
    try {
      var result = LOCALSTACK.execInContainer("awslocal", "s3", "mb", "s3://" + BUCKET);
      if (result.getExitCode() != 0) {
        throw new IllegalStateException("LocalStack にバケットを作れませんでした: " + result.getStderr());
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  /** Mailpit の API の URL（http://ホスト:ポート） */
  static String mailpitApiUrl() {
    return "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(MAILPIT_API_PORT);
  }

  private static DockerImageName fitLogPostgresImage() {
    String image =
        new ImageFromDockerfile("fitlog-postgres:local", false)
            .withFileFromPath(".", Path.of("../db"))
            .get();
    return DockerImageName.parse(image).asCompatibleSubstituteFor("postgres");
  }

  /** application-test.yml の接続先（つながらない宛先）を、コンテナの接続先で上書きする */
  @Bean
  DynamicPropertyRegistrar containerProperties() {
    return registry -> {
      registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
      registry.add("spring.datasource.username", POSTGRES::getUsername);
      registry.add("spring.datasource.password", POSTGRES::getPassword);
      registry.add("spring.mail.host", MAILPIT::getHost);
      registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(MAILPIT_SMTP_PORT));
      registry.add("app.storage.endpoint", () -> LOCALSTACK.getEndpoint().toString());
      registry.add("app.storage.region", LOCALSTACK::getRegion);
      registry.add("app.storage.bucket", () -> BUCKET);
    };
  }
}
