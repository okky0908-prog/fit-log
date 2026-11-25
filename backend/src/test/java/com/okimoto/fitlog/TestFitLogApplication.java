package com.okimoto.fitlog;

import com.okimoto.fitlog.support.TestcontainersConfiguration;
import org.springframework.boot.SpringApplication;

/**
 * E2E テスト（frontend/e2e）用に、バックエンドを使い捨てのコンテナ（PostgreSQL・Mailpit・LocalStack）につないで起動する。
 *
 * <p>{@code ./gradlew bootTestRun} で起動する。テストと同じ {@link TestcontainersConfiguration} と
 * application-test.yml を使うので、開発用の DB・メール・S3（docker-compose）には書き込まない。止めるとコンテナも消え、データは残らない。 ポートは開発と同じ
 * 8080（CLAUDE.md）なので、開発用のバックエンドを止めてから起動する
 */
public class TestFitLogApplication {

  public static void main(String[] args) {
    SpringApplication.from(FitLogApplication::main)
        .with(TestcontainersConfiguration.class)
        .withAdditionalProfiles("test")
        .run(args);
  }
}
