package com.venus.minio.learn.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.venus.minio.learn.demo.DemoFiles;

import io.minio.GetObjectArgs;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;

@EnabledIfEnvironmentVariable(named = "MINIO_IT", matches = "true")
class MultipartIT extends MinioItBase {

    private static final long SIZE = 20L * 1024 * 1024;
    private static final long PART_SIZE = 5L * 1024 * 1024;

    @Test
    void multipartUpload() throws Exception {
        String bucket = newBucket();
        Path file = DemoFiles.createSizedFile("it-20m.bin", SIZE);
        try {
            String etag;
            try (InputStream in = Files.newInputStream(file)) {
                etag = client.putObject(PutObjectArgs.builder()
                        .bucket(bucket).object("big/20m.bin")
                        .stream(in, SIZE, PART_SIZE)
                        .contentType("application/octet-stream")
                        .build()).etag();
            }
            assertThat(etag).contains("-");
            assertThat(etag.replace("\"", "")).endsWith("-4");
            assertThat(client.statObject(StatObjectArgs.builder()
                    .bucket(bucket).object("big/20m.bin").build()).size()).isEqualTo(SIZE);

            try (var response = client.getObject(GetObjectArgs.builder()
                    .bucket(bucket).object("big/20m.bin")
                    .offset(SIZE - 1024).length(1024L).build())) {
                assertThat(response.readAllBytes()).hasSize(1024);
            }
        } finally {
            DemoFiles.deleteQuietly(file);
        }
    }
}
