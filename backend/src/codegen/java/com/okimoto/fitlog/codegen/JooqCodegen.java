package com.okimoto.fitlog.codegen;

import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.Database;
import org.jooq.meta.jaxb.Generate;
import org.jooq.meta.jaxb.Generator;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.jaxb.Target;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * マイグレーションから jOOQ のコードを作る（Gradle の generateJooq タスクから動かす。docs/tech-stack.md 3.3）。
 *
 * <ol>
 *   <li>pg_bigm 入りの PostgreSQL のイメージを db/Dockerfile から作り、使い捨てのコンテナで立ち上げる
 *   <li>Flyway でマイグレーションを当てる（アプリと同じ SQL でテーブルを作る）
 *   <li>できたテーブルから、jOOQ のコードを作る
 * </ol>
 *
 * <p>引数：db/ のフォルダ、マイグレーションのフォルダ、コードを書き出すフォルダ、コードのパッケージ名
 */
public final class JooqCodegen {

  private JooqCodegen() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 4) {
      throw new IllegalArgumentException("引数は4つ（db のフォルダ・マイグレーションのフォルダ・書き出し先・パッケージ名）");
    }
    Path dbImageDir = Path.of(args[0]);
    Path migrationDir = Path.of(args[1]);
    String outputDir = args[2];
    String packageName = args[3];

    try (PostgreSQLContainer postgres = new PostgreSQLContainer(fitLogPostgresImage(dbImageDir))) {
      postgres.start();

      Flyway.configure()
          .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
          .locations("filesystem:" + migrationDir.toAbsolutePath())
          .load()
          .migrate();

      GenerationTool.generate(
          new Configuration()
              .withJdbc(
                  new Jdbc()
                      .withDriver("org.postgresql.Driver")
                      .withUrl(postgres.getJdbcUrl())
                      .withUser(postgres.getUsername())
                      .withPassword(postgres.getPassword()))
              .withGenerator(
                  new Generator()
                      .withDatabase(
                          new Database()
                              .withName("org.jooq.meta.postgres.PostgresDatabase")
                              .withInputSchema("public")
                              // Flyway の管理用の表と、pg_bigm の関数はコードにしない
                              .withExcludes("flyway_schema_history")
                              .withIncludeRoutines(false))
                      .withGenerate(new Generate().withJavaTimeTypes(true))
                      .withTarget(
                          new Target().withPackageName(packageName).withDirectory(outputDir))));
    }
  }

  /**
   * db/Dockerfile から pg_bigm 入りのイメージを作る。終わっても消さないので、2回目からは Docker のキャッシュで速く終わる。 テストの
   * TestcontainersConfiguration と同じ名前にして、同じイメージを使い回す
   */
  static DockerImageName fitLogPostgresImage(Path dbImageDir) {
    String image =
        new ImageFromDockerfile("fitlog-postgres:local", false)
            .withFileFromPath(".", dbImageDir)
            .get();
    return DockerImageName.parse(image).asCompatibleSubstituteFor("postgres");
  }
}
