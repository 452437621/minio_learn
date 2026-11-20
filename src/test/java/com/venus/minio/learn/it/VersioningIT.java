package com.venus.minio.learn.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import io.minio.GetBucketVersioningArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.SetBucketVersioningArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import io.minio.messages.VersioningConfiguration;

@EnabledIfEnvironmentVariable(named = "MINIO_IT", matches = "true")
class VersioningIT extends MinioItBase {

    @Test
    void versionsDeleteMarkerAndSuspend() throws Exception {
        String bucket = newBucket();
        String key = "docs/report.txt";
        client.setBucketVersioning(SetBucketVersioningArgs.builder().bucket(bucket)
                .config(new VersioningConfiguration(VersioningConfiguration.Status.ENABLED, null))
                .build());
        assertThat(client.getBucketVersioning(GetBucketVersioningArgs.builder().bucket(bucket).build())
                .status()).isEqualTo(VersioningConfiguration.Status.ENABLED);

        String v1 = put(bucket, key, "version one");
        String v2 = put(bucket, key, "version two");
        assertThat(v1).isNotEqualTo(v2);

        List<Item> versions = listVersions(bucket, key);
        assertThat(versions).hasSizeGreaterThanOrEqualTo(2);
        assertThat(versions).anyMatch(Item::isLatest);

        try (var response = client.getObject(GetObjectArgs.builder()
                .bucket(bucket).object(key).versionId(v1).build())) {
            assertThat(new String(response.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("version one");
        }

        client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        assertThatThrownBy(() -> client.getObject(GetObjectArgs.builder()
                .bucket(bucket).object(key).build()).close())
                .isInstanceOf(ErrorResponseException.class);
        assertThat(listVersions(bucket, key)).anyMatch(Item::isDeleteMarker);

        client.setBucketVersioning(SetBucketVersioningArgs.builder().bucket(bucket)
                .config(new VersioningConfiguration(VersioningConfiguration.Status.SUSPENDED, null))
                .build());
        String suspended = put(bucket, key, "suspended write");
        assertThat(suspended == null || "null".equals(suspended)).isTrue();
    }

    private String put(String bucket, String key, String text) throws Exception {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return client.putObject(PutObjectArgs.builder()
                .bucket(bucket).object(key)
                .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                .contentType("text/plain; charset=utf-8")
                .build()).versionId();
    }

    private List<Item> listVersions(String bucket, String key) throws Exception {
        List<Item> items = new ArrayList<>();
        for (Result<Item> result : client.listObjects(ListObjectsArgs.builder()
                .bucket(bucket).prefix(key).recursive(true).includeVersions(true).build())) {
            Item item = result.get();
            if (item.objectName().equals(key)) {
                items.add(item);
            }
        }
        return items;
    }
}
