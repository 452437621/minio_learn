package com.venus.minio.learn.demo;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import io.minio.GetBucketVersioningArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketVersioningArgs;
import io.minio.messages.Item;
import io.minio.messages.VersioningConfiguration;

@Component
public class VersioningDemo implements Demo {

    @Override
    public String name() {
        return "versioning";
    }

    @Override
    public String description() {
        return "版本控制、历史版本、删除标记与挂起状态";
    }

    @Override
    public void run(DemoSupport s) throws Exception {
        s.header("VersioningDemo - 版本控制");
        String bucket = "learn-version";
        String key = "docs/report.txt";
        s.createFreshBucket(bucket);

        s.client().setBucketVersioning(SetBucketVersioningArgs.builder().bucket(bucket)
                .config(new VersioningConfiguration(VersioningConfiguration.Status.ENABLED, null))
                .build());
        VersioningConfiguration enabled = s.client().getBucketVersioning(
                GetBucketVersioningArgs.builder().bucket(bucket).build());
        s.checkEquals(VersioningConfiguration.Status.ENABLED, enabled.status(), "版本控制已启用");

        String v1Id = put(s, bucket, key, "version one");
        String v2Id = put(s, bucket, key, "version two");
        s.check(!Objects.equals(v1Id, v2Id), "两次 PUT 生成不同 versionId");

        List<Item> versions = s.listItems(bucket, key, true).stream()
                .filter(i -> i.objectName().equals(key)).toList();
        s.check(versions.size() >= 2, "includeVersions 列出至少 2 个版本");
        s.check(versions.stream().anyMatch(Item::isLatest), "存在 isLatest=true 的当前版本");
        s.checkEquals("version two", s.getText(bucket, key), "不带 versionId 读取到最新版本");

        try (GetObjectResponse response = s.client().getObject(GetObjectArgs.builder()
                .bucket(bucket).object(key).versionId(v1Id).build())) {
            s.checkEquals("version one",
                    new String(response.readAllBytes(), StandardCharsets.UTF_8),
                    "按 versionId 读取旧版本");
        }

        s.client().removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        s.expectException("删除最新版本后直接 GET", io.minio.errors.ErrorResponseException.class,
                () -> s.getText(bucket, key));
        List<Item> afterDelete = s.listItems(bucket, key, true).stream()
                .filter(i -> i.objectName().equals(key)).toList();
        s.check(afterDelete.stream().anyMatch(Item::isDeleteMarker),
                "删除操作产生删除标记 isDeleteMarker=true");
        s.note("历史版本仍在，可通过 versionId 恢复: " + v1Id);

        s.client().setBucketVersioning(SetBucketVersioningArgs.builder().bucket(bucket)
                .config(new VersioningConfiguration(VersioningConfiguration.Status.SUSPENDED, null))
                .build());
        String suspendedId = put(s, bucket, key, "written while suspended");
        s.check(suspendedId == null || "null".equals(suspendedId),
                "SUSPENDED 状态下写入的版本 ID 为 null（实际: " + suspendedId + "）");
        s.cleanupBucket(bucket);
    }

    private String put(DemoSupport s, String bucket, String key, String text) throws Exception {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return s.client().putObject(PutObjectArgs.builder()
                .bucket(bucket).object(key)
                .stream(new java.io.ByteArrayInputStream(bytes), bytes.length, -1)
                .contentType("text/plain; charset=utf-8")
                .build()).versionId();
    }
}
