package com.venus.minio.learn.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;

/**
 * Web 模式启动时确保默认桶存在；命令行演示模式（--demo=）跳过。
 */
@Component
public class BucketInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BucketInitializer.class);

    private final MinioClient client;
    private final MinioProperties properties;

    public BucketInitializer(MinioClient client, MinioProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        var demoValues = args.getOptionValues("demo");
        if (demoValues != null && !demoValues.isEmpty()) {
            return;
        }
        if (!properties.autoCreateBucket()) {
            return;
        }
        String bucket = properties.bucket();
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("已创建默认桶: {}", bucket);
            } else {
                log.info("默认桶已存在: {}", bucket);
            }
        } catch (ErrorResponseException e) {
            String code = e.errorResponse().code();
            if ("BucketAlreadyOwnedByYou".equals(code) || "BucketAlreadyExists".equals(code)) {
                log.info("默认桶已存在: {}", bucket);
                return;
            }
            handleFailure(e);
        } catch (Exception e) {
            handleFailure(e);
        }
    }

    private void handleFailure(Exception e) {
        if (properties.failFast()) {
            throw new IllegalStateException("初始化默认桶失败，请检查 Silo 是否在 "
                    + properties.endpoint() + " 运行", e);
        }
        log.warn("初始化默认桶失败（Web 页面仍可打开）: {}", e.getMessage());
    }
}
