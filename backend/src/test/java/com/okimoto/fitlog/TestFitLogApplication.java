package com.okimoto.fitlog;

import com.okimoto.fitlog.support.TestcontainersConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;

/**
 * E2E テスト（frontend/e2e）用に、バックエンドを使い捨てのコンテナ（PostgreSQL・Mailpit・LocalStack）につないで起動する。
 *
 * <p>{@code ./gradlew bootTestRun} で起動する。テストと同じ {@link TestcontainersConfiguration} と
 * application-test.yml を使うので、開発用の DB・メール・S3（docker-compose）には書き込まない。止めるとコンテナも消え、データは残らない。 ポートは開発と同じ
 * 8080（CLAUDE.md）なので、開発用のバックエンドを止めてから起動する。
 *
 * <p>E2E が届いた確認メールを読めるよう、使い捨ての Mailpit の API の URL（ポートは起動のたびに変わる）を {@link #MAILPIT_URL_FILE} に書き出す
 */
public class TestFitLogApplication {

  /** Mailpit の API の URL を書き出すファイル（backend の作業フォルダーから見た場所。frontend/e2e/support.ts が読む） */
  static final Path MAILPIT_URL_FILE = Path.of("build", "e2e", "mailpit-url.txt");

  public static void main(String[] args) throws IOException {
    SpringApplication.from(FitLogApplication::main)
        .with(TestcontainersConfiguration.class)
        .withAdditionalProfiles("test")
        .run(args);
    Files.createDirectories(MAILPIT_URL_FILE.getParent());
    Files.writeString(MAILPIT_URL_FILE, TestcontainersConfiguration.mailpitApiUrl());
  }
}
