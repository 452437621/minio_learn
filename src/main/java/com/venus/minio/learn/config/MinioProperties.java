package com.venus.minio.learn.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO / Silo 连接配置。
 *
 * <p>注意：{@code endpoint} 必须是 S3 API 端口（本机为 9000），
 * 而不是控制台端口 9001。</p>
 */
@ConfigurationProperties(prefix = "learn.minio")
public record MinioProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket,
        String consoleUrl,
        boolean autoCreateBucket,
        boolean failFast,
        int presignDefaultSeconds) {
}
