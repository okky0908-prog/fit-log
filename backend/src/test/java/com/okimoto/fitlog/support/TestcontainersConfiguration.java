package com.okimoto.fitlog.support;

import java.nio.file.Path;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * テスト用の PostgreSQL（pg_bigm 入りの自作イメージ）と Mailpit（メール）を、Testcontainers の使い捨てのコンテナで立ち上げる。
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
 *   <li>LocalStack は、画像の機能を作るときに足す（docs/test-plan.md 8.1）
 * </ul>
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(fitLogPostgresImage()).withDatabaseName("fitlog");

  /** Mailpit の SMTP（1025）と、届いたメールを見る API（8025） */
  private static final int MAILPIT_SMTP_PORT = 1025;

  private static final int MAILPIT_API_PORT = 8025;

  private static final GenericContainer<?> MAILPIT =
      new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.33.1"))
          .withExposedPorts(MAILPIT_SMTP_PORT, MAILPIT_API_PORT);

  static {
    POSTGRES.start();
    MAILPIT.start();
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
    };
  }
}
