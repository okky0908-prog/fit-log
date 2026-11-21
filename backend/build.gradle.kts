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

val jjwtVersion = "0.13.0"
val awsSdkVersion = "2.59.7"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    // メールの送信（手元の開発とテストは SMTP で Mailpit に送る。docs/tech-stack.md 3.8）
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.flywaydb:flyway-database-postgresql")
    // JWT。JSON の処理は Gson を使う（jjwt-jackson は Jackson 2 に依存し、Spring Boot 4 の Jackson 3 と混ざるため。docs/tech-stack.md）
    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-gson:$jjwtVersion")
    runtimeOnly("org.postgresql:postgresql")
    // 記録の画像の保存先（S3。手元の開発とテストは LocalStack）と、署名付き URL（docs/tech-stack.md 3.9）。
    // 非同期の通信（Netty）は使わないので外す
    implementation(platform("software.amazon.awssdk:bom:$awsSdkVersion"))
    implementation("software.amazon.awssdk:s3") {
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
    }
    // 画像の付属データ（EXIF・XMP）を読み、位置情報を含む画像を受け付けない（docs/api.md A-20）
    implementation("com.drewnoakes:metadata-extractor:2.22.0")

    // jOOQ のコード生成（版は、アプリが使う jOOQ と同じく Spring Boot が管理する）
    "codegenImplementation"("org.jooq:jooq-codegen")
    "codegenImplementation"("org.flywaydb:flyway-database-postgresql")
    "codegenImplementation"("org.testcontainers:testcontainers-postgresql")
    "codegenRuntimeOnly"("org.postgresql:postgresql")
    "codegenRuntimeOnly"("org.slf4j:slf4j-simple")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-jooq-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    // 保存の確定後に別のスレッドで動く処理（確認メールの送信など）が終わるのを待つ（Spring Boot が版を管理する）
    testImplementation("org.awaitility:awaitility")
    // テストは、使い捨ての PostgreSQL（pg_bigm 入りの自作イメージ）のコンテナで動かす（開発用の DB に書き込まない）
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-localstack")
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
    // JVM（と、その設定を引き継ぐ DB の接続）のタイムゾーンを UTC にする。既定のタイムゾーンに頼った書き漏れがあると、
    // 日本時間の0時〜9時の境界のテストが落ちて見つかる（docs/test-plan.md 4.4）
    jvmArgs("-Duser.timezone=UTC")
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
