package com.venus.minio.learn.demo;

import org.springframework.stereotype.Component;

import io.minio.RemoveBucketArgs;
import io.minio.errors.ErrorResponseException;

@Component
public class BucketDemo implements Demo {

    @Override
    public String name() {
        return "bucket";
    }

    @Override
    public String description() {
        return "桶的创建、列举、非空保护与删除";
    }

    @Override
    public void run(DemoSupport s) throws Exception {
        s.header("BucketDemo - 桶 CRUD 与非空保护");
        String bucket = "learn-bucket";
        s.createFreshBucket(bucket);
        s.check(s.bucketExists(bucket), "bucketExists 返回 true");
        s.check(s.client().listBuckets().stream().anyMatch(b -> b.name().equals(bucket)),
                "listBuckets 能看到 " + bucket);

        s.putText(bucket, "hello.txt", "hello bucket", "text/plain; charset=utf-8");
        s.note("桶内已有对象，直接删除应被 S3 拒绝（BucketNotEmpty）");
        Exception e = s.expectException("删除非空桶", ErrorResponseException.class,
                () -> s.client().removeBucket(RemoveBucketArgs.builder().bucket(bucket).build()));
        s.note("服务端错误: " + s.errorCode(e));

        s.purgeBucket(bucket);
        s.client().removeBucket(RemoveBucketArgs.builder().bucket(bucket).build());
        s.check(!s.bucketExists(bucket), "清空对象后桶可以删除");
        s.cleanupBucket(bucket);
    }
}
