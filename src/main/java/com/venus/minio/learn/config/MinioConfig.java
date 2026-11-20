package com.venus.minio.learn.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.minio.MinioClient;

@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(MinioProperties properties) {
        if (properties.endpoint() == null || properties.endpoint().isBlank()) {
            throw new IllegalStateException("learn.minio.endpoint 不能为空");
        }
        if (properties.accessKey() == null || properties.secretKey() == null) {
            throw new IllegalStateException("learn.minio.access-key / secret-key 不能为空");
        }
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }
}
