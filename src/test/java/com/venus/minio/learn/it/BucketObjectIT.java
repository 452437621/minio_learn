package com.venus.minio.learn.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.PutObjectArgs;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;

@EnabledIfEnvironmentVariable(named = "MINIO_IT", matches = "true")
class BucketObjectIT extends MinioItBase {

    @Test
    void bucketAndObjectCrud() throws Exception {
        String bucket = newBucket();
        assertThat(client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())).isTrue();

        String key = "docs/hello.txt";
        String copyKey = "copy/hello-copy.txt";
        byte[] body = "hello integration test".getBytes(StandardCharsets.UTF_8);
        client.putObject(PutObjectArgs.builder()
                .bucket(bucket).object(key)
                .stream(new ByteArrayInputStream(body), body.length, -1)
                .contentType("text/plain; charset=utf-8")
                .userMetadata(java.util.Map.of("author", "it"))
                .build());

        var stat = client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
        assertThat(stat.size()).isEqualTo(body.length);
        assertThat(stat.userMetadata()).containsEntry("author", "it");

        try (var response = client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            assertThat(new String(response.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("hello integration test");
        }

        // 非空桶删除保护
        assertThatThrownBy(() -> client.removeBucket(RemoveBucketArgs.builder().bucket(bucket).build()))
                .isInstanceOf(ErrorResponseException.class);

        client.copyObject(CopyObjectArgs.builder()
                .bucket(bucket).object(copyKey)
                .source(CopySource.builder().bucket(bucket).object(key).build())
                .build());
        try (var response = client.getObject(GetObjectArgs.builder().bucket(bucket).object(copyKey).build())) {
            assertThat(new String(response.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("hello integration test");
        }

        boolean listed = false;
        for (Result<Item> result : client.listObjects(ListObjectsArgs.builder()
                .bucket(bucket).recursive(true).prefix("copy/").build())) {
            if (result.get().objectName().equals(copyKey)) {
                listed = true;
            }
        }
        assertThat(listed).isTrue();

        client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        assertThatThrownBy(() -> client.statObject(
                StatObjectArgs.builder().bucket(bucket).object(key).build()))
                .isInstanceOf(ErrorResponseException.class);
    }
}
