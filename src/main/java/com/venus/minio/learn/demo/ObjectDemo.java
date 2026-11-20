package com.venus.minio.learn.demo;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;

@Component
public class ObjectDemo implements Demo {

    @Override
    public String name() {
        return "object";
    }

    @Override
    public String description() {
        return "对象上传/下载/元数据/复制/Range/列举/删除";
    }

    @Override
    public void run(DemoSupport s) throws Exception {
        s.header("ObjectDemo - 对象 CRUD 与元数据");
        String bucket = "learn-object";
        String key = "docs/hello.txt";
        String copyKey = "copy/hello-copy.txt";
        s.createFreshBucket(bucket);

        byte[] content = "Hello MinIO / Silo 对象存储！".getBytes(StandardCharsets.UTF_8);
        s.step("PUT 对象，并附带 contentType、用户元数据和对象标签");
        s.client().putObject(PutObjectArgs.builder()
                .bucket(bucket).object(key)
                .stream(new ByteArrayInputStream(content), content.length, -1)
                .contentType("text/plain; charset=utf-8")
                .userMetadata(Map.of("author", "venus", "course", "minio-learn"))
                .tags(Map.of("env", "learn", "level", "basic"))
                .build());
        s.ok("putObject 成功: " + key);

        StatObjectResponse stat = s.client().statObject(StatObjectArgs.builder()
                .bucket(bucket).object(key).build());
        s.checkEquals((long) content.length, stat.size(), "stat.size 与写入字节数一致");
        s.checkEquals("venus", stat.userMetadata().get("author"), "stat.userMetadata.author=venus");
        s.note("contentType = " + stat.contentType() + ", etag = " + stat.etag());

        try (GetObjectResponse response = s.client().getObject(GetObjectArgs.builder()
                .bucket(bucket).object(key).build())) {
            String text = new String(response.readAllBytes(), StandardCharsets.UTF_8);
            s.checkEquals("Hello MinIO / Silo 对象存储！", text, "GET 内容与写入一致");
        }

        byte[] head = s.readRange(bucket, key, 0, 5);
        s.checkEquals("Hello", new String(head, StandardCharsets.US_ASCII), "Range GET 前 5 字节为 Hello");

        s.client().copyObject(CopyObjectArgs.builder()
                .bucket(bucket).object(copyKey)
                .source(CopySource.builder().bucket(bucket).object(key).build())
                .build());
        s.checkEquals("Hello MinIO / Silo 对象存储！", s.getText(bucket, copyKey), "copyObject 复制成功");

        s.check(s.listItems(bucket, "").stream().anyMatch(i -> i.objectName().equals(key)),
                "递归列出包含 " + key);
        s.check(s.listItems(bucket, "copy/").stream().anyMatch(i -> i.objectName().equals(copyKey)),
                "前缀列出 copy/ 包含复制后的对象");
        for (Item item : s.listItems(bucket, "")) {
            s.note("对象: " + item.objectName() + " (" + item.size() + " bytes)");
        }

        s.client().removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        Exception e = s.expectException("删除后 stat 原对象", ErrorResponseException.class,
                () -> s.client().statObject(StatObjectArgs.builder().bucket(bucket).object(key).build()));
        s.note("服务端错误: " + s.errorCode(e));
        s.cleanupBucket(bucket);
    }
}
