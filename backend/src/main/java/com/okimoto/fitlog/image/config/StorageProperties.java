package com.okimoto.fitlog.image.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 画像の保存先（application.yml の app.storage）。
 *
 * @param bucket S3 のバケット
 * @param region S3 のリージョン
 * @param endpoint 接続先の URL。手元の開発・テストでは LocalStack。空なら AWS の S3（本番。ECS のタスクの IAM ロールの権限で接続する）
 */
@Validated
@ConfigurationProperties("app.storage")
public record StorageProperties(@NotBlank String bucket, @NotBlank String region, String endpoint) {

  /** LocalStack など、AWS 以外の S3 互換の接続先を使うか */
  public boolean usesCustomEndpoint() {
    return endpoint != null && !endpoint.isBlank();
  }
}
