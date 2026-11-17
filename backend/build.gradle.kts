plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.11.0"
}

group = "com.okimoto.fitlog"
version = "0.0.1-SNAPSHOT"
description = "FIT LOG バックエンド"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

// jOOQ のコードを作るプログラム（src/codegen）。アプリとは別のソースセットにして、アプリの jar に入れない
val codegen by sourceSets.creating

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // jOOQ のコード生成（版は、アプリが使う jOOQ と同じく Spring Boot が管理する）
    "codegenImplementation"("org.jooq:jooq-codegen")
    "codegenImplementation"("org.flywaydb:flyway-database-postgresql")
    "codegenImplementation"("org.testcontainers:testcontainers-postgresql")
    "codegenRuntimeOnly"("org.postgresql:postgresql")
    "codegenRuntimeOnly"("org.slf4j:slf4j-simple")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-jooq-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    // テストは、使い捨ての PostgreSQL（pg_bigm 入りの自作イメージ）のコンテナで動かす（開発用の DB に書き込まない）
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// jOOQ のコード生成（docs/tech-stack.md 3.3）。
// pg_bigm 入りの PostgreSQL（../db/Dockerfile）を Testcontainers で立ち上げ、Flyway でテーブルを作ってから、テーブルの形のコードを作る。
// マイグレーション・Dockerfile・生成のプログラムが変わらない限り、Gradle は前回の結果を使い、コンテナを立ち上げない。
// 生成したコードはリポジトリに入れない（build/ の下に作る）
val migrationDir = layout.projectDirectory.dir("src/main/resources/db/migration")
val dbImageDir = layout.projectDirectory.dir("../db")
val jooqOutputDir = layout.buildDirectory.dir("generated-sources/jooq")

val generateJooq by tasks.registering(JavaExec::class) {
    group = "build"
    description = "マイグレーションから jOOQ のコードを作る（Docker が必要）"
    classpath = codegen.runtimeClasspath
    mainClass = "com.okimoto.fitlog.codegen.JooqCodegen"
    // jOOQ のロゴ・今日のヒントと、Testcontainers の起動のログを出さない（警告とエラーだけを出す）
    systemProperty("org.jooq.no-logo", "true")
    systemProperty("org.jooq.no-tips", "true")
    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn")
    // DB の版の確認と警告を止める（application.yml の logging.level と同じ理由）
    systemProperty("org.slf4j.simpleLogger.log.org.jooq.impl.DefaultExecuteContext.logVersionSupport", "error")
    inputs.dir(migrationDir).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(dbImageDir).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(jooqOutputDir)
    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                dbImageDir.asFile.absolutePath,
                migrationDir.asFile.absolutePath,
                jooqOutputDir.get().asFile.absolutePath,
                "com.okimoto.fitlog.jooq",
            )
        },
    )
}

sourceSets.main {
    java.srcDir(generateJooq)
}

tasks.withType<Test> {
    useJUnitPlatform()
    // テストのあとに、カバレッジ（どの行・分岐をテストが通ったか）のレポートを作る
    finalizedBy(tasks.jacocoTestReport)
}

// テストのカバレッジ（docs/test-plan.md 6章）。Java 25 のクラスを読めるよう、0.8.14 以上を使う
jacoco {
    toolVersion = "0.8.15"
}

// jOOQ が生成したコードは、テストの対象にしない（カバレッジの数に入れない）
val coverageClasses =
    files(
        sourceSets.main.map { main ->
            main.output.classesDirs.asFileTree
                .matching { exclude("com/okimoto/fitlog/jooq/**") }
        },
    )

// レポートは build/reports/jacoco/test/ に作る（html：ブラウザで見る）
tasks.jacocoTestReport {
    classDirectories.setFrom(coverageClasses)
    reports {
        html.required = true
        xml.required = true
    }
}

// 層ごとの分岐網羅の基準（docs/test-plan.md 6章）。クラスごとに確かめ、1つでも下回ったら check（CI）を落とす。
// 層はパッケージで分ける（LayerPackageTest が、どの層にも入らないクラスがないことを確かめる）。
// 分岐のないクラスは、基準の対象にならない（JaCoCo は数のない項目を確かめない）
tasks.jacocoTestCoverageVerification {
    classDirectories.setFrom(coverageClasses)
    violationRules {
        rule {
            element = "CLASS"
            includes = listOf("com.okimoto.fitlog.*.calc.*")
            limit {
                counter = "BRANCH"
                minimum = "1.00".toBigDecimal()
            }
        }
        rule {
            element = "CLASS"
            includes = listOf("com.okimoto.fitlog.*.service.*")
            limit {
                counter = "BRANCH"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

spotless {
    java {
        // jOOQ が生成したコード（build/ の下）は整形しない
        target("src/**/*.java")
        googleJavaFormat()
        removeUnusedImports()
        importOrder()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}
