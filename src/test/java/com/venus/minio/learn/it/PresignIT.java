package com.venus.minio.learn.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.http.Method;

@EnabledIfEnvironmentVariable(named = "MINIO_IT", matches = "true")
class PresignIT extends MinioItBase {

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void presignedGetAndPut() throws Exception {
        String bucket = newBucket();
        String key = "reports/hello.txt";
        byte[] original = "presigned body".getBytes(StandardCharsets.UTF_8);
        client.putObject(PutObjectArgs.builder()
                .bucket(bucket).object(key)
                .stream(new ByteArrayInputStream(original), original.length, -1)
                .contentType("text/plain; charset=utf-8")
                .build());

        String getUrl = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET).bucket(bucket).object(key)
                .expiry(10, TimeUnit.MINUTES).build());
        HttpResponse<String> get = http.send(HttpRequest.newBuilder(URI.create(getUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(get.statusCode()).isEqualTo(200);
        assertThat(get.body()).isEqualTo("presigned body");

        String putKey = "uploads/put-by-url.txt";
        String putUrl = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.PUT).bucket(bucket).object(putKey)
                .expiry(10, TimeUnit.MINUTES).build());
        byte[] uploaded = "uploaded by presigned PUT".getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> put = http.send(HttpRequest.newBuilder(URI.create(putUrl))
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(uploaded)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(put.statusCode()).isEqualTo(200);
        assertThat(client.statObject(StatObjectArgs.builder()
                .bucket(bucket).object(putKey).build()).size()).isEqualTo(uploaded.length);

        assertThatThrownBy(() -> client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET).bucket(bucket).object(key)
                .expiry(8, TimeUnit.DAYS).build()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
