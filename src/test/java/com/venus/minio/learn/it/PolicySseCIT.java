package com.venus.minio.learn.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import io.minio.DeleteBucketPolicyArgs;
import io.minio.GetObjectArgs;
import io.minio.PutObjectArgs;
import io.minio.ServerSideEncryptionCustomerKey;
import io.minio.SetBucketPolicyArgs;
import io.minio.errors.ErrorResponseException;

@EnabledIfEnvironmentVariable(named = "MINIO_IT", matches = "true")
class PolicySseCIT extends MinioItBase {

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void anonymousReadPolicy() throws Exception {
        String bucket = newBucket();
        String key = "public/hello.txt";
        byte[] body = "public body".getBytes(StandardCharsets.UTF_8);
        client.putObject(PutObjectArgs.builder()
                .bucket(bucket).object(key)
                .stream(new ByteArrayInputStream(body), body.length, -1)
                .contentType("text/plain; charset=utf-8")
                .build());

        String url = endpoint() + "/" + bucket + "/" + key;
        assertThat(get(url)).isEqualTo(403);

        String policy = """
                {"Version":"2012-10-17","Statement":[{"Effect":"Allow",
                "Principal":{"AWS":["*"]},"Action":["s3:GetObject"],
                "Resource":["arn:aws:s3:::%s/*"]}]}
                """.formatted(bucket);
        client.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build());
        assertThat(get(url)).isEqualTo(200);

        client.deleteBucketPolicy(DeleteBucketPolicyArgs.builder().bucket(bucket).build());
        assertThat(get(url)).isEqualTo(403);
    }

    @Test
    void sseCUsesCustomerKey() throws Exception {
        String bucket = newBucket();
        String key = "secret/report.txt";
        byte[] rawKey = new byte[32];
        new SecureRandom().nextBytes(rawKey);
        var ssec = new ServerSideEncryptionCustomerKey(new SecretKeySpec(rawKey, "AES"));
        byte[] body = "confidential".getBytes(StandardCharsets.UTF_8);

        if (!endpoint().toLowerCase().startsWith("https://")) {
            // S3 规范要求 SSE-C 必须通过 HTTPS；SDK 会在本地拒绝。
            assertThatThrownBy(() -> client.putObject(PutObjectArgs.builder()
                    .bucket(bucket).object(key)
                    .stream(new ByteArrayInputStream(body), body.length, -1)
                    .contentType("text/plain; charset=utf-8")
                    .sse(ssec)
                    .build()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("secure connection");
            return;
        }

        client.putObject(PutObjectArgs.builder()
                .bucket(bucket).object(key)
                .stream(new ByteArrayInputStream(body), body.length, -1)
                .contentType("text/plain; charset=utf-8")
                .sse(ssec)
                .build());

        try (var response = client.getObject(GetObjectArgs.builder()
                .bucket(bucket).object(key).ssec(ssec).build())) {
            assertThat(new String(response.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("confidential");
        }

        assertThatThrownBy(() -> client.getObject(GetObjectArgs.builder()
                .bucket(bucket).object(key).build()).close())
                .isInstanceOf(ErrorResponseException.class)
                .satisfies(e -> assertThat(((ErrorResponseException) e).response().code())
                        .isEqualTo(400));
    }

    private String endpoint() {
        return properties.endpoint().replaceAll("/+$", "");
    }

    private int get(String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode();
    }
}
