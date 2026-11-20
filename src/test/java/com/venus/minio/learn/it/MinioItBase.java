package com.venus.minio.learn.it;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.venus.minio.learn.config.MinioProperties;
import com.venus.minio.learn.demo.DemoSupport;

import io.minio.MakeBucketArgs;
import io.minio.MinioClient;

/**
 * 集成测试基类：连接本机 Silo（127.0.0.1:9000）。
 * 未设置环境变量 MINIO_IT=true 时，各测试类会被 JUnit 跳过。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"learn.minio.auto-create-bucket=false", "demo="})
public abstract class MinioItBase {

    @Autowired
    protected MinioClient client;

    @Autowired
    protected MinioProperties properties;

    private final List<String> createdBuckets = new CopyOnWriteArrayList<>();

    protected String newBucket() throws Exception {
        return newBucket("it");
    }

    protected String newBucket(String prefix) throws Exception {
        String name = prefix + "-" + Long.toString(System.currentTimeMillis(), 36) + "-"
                + UUID.randomUUID().toString().substring(0, 6);
        client.makeBucket(MakeBucketArgs.builder().bucket(name).build());
        createdBuckets.add(name);
        return name;
    }

    protected String newObjectLockBucket() throws Exception {
        String name = "it-lock-" + Long.toString(System.currentTimeMillis(), 36) + "-"
                + UUID.randomUUID().toString().substring(0, 6);
        client.makeBucket(MakeBucketArgs.builder().bucket(name).objectLock(true).build());
        createdBuckets.add(name);
        return name;
    }

    @AfterEach
    void cleanBuckets() {
        DemoSupport support = new DemoSupport(client, properties, false);
        for (String bucket : createdBuckets) {
            try {
                support.deleteBucketFully(bucket);
            } catch (Exception e) {
                System.err.println("[IT cleanup] 清理桶 " + bucket + " 失败: " + e.getMessage());
            }
        }
        createdBuckets.clear();
    }
}
