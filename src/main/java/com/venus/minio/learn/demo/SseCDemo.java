package com.venus.minio.learn.demo;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.PutObjectArgs;
import io.minio.ServerSideEncryptionCustomerKey;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;

@Component
public class SseCDemo implements Demo {

    @Override
    public String name() {
        return "ssec";
    }

    @Override
    public String description() {
        return "SSE-C 客户端密钥：HTTP 下安全拦截，HTTPS 下完整加解密";
    }

    @Override
    public void run(DemoSupport s) throws Exception {
        s.header("SseCDemo - 服务端加密（客户提供密钥）");
        String bucket = "learn-ssec";
        String key = "secret/report.txt";
        String text = "confidential payload";
        s.createFreshBucket(bucket);

        byte[] rawKey = new byte[32];
        new SecureRandom().nextBytes(rawKey);
        SecretKey secretKey = new SecretKeySpec(rawKey, "AES");
        ServerSideEncryptionCustomerKey ssec = new ServerSideEncryptionCustomerKey(secretKey);
        s.note("本次演示密钥（Base64，仅学习用，切勿用于生产）: "
                + Base64.getEncoder().encodeToString(rawKey));

        if (!isHttps(s)) {
            s.note("SSE-C 按 S3 规范必须通过 HTTPS 传输密钥；当前 endpoint = "
                    + s.properties().endpoint());
            Exception e = s.expectException("SDK 在 HTTP 上拒绝 SSE-C 写入",
                    IllegalArgumentException.class,
                    () -> s.client().putObject(PutObjectArgs.builder()
                            .bucket(bucket).object(key)
                            .stream(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),
                                    text.getBytes(StandardCharsets.UTF_8).length, -1)
                            .contentType("text/plain; charset=utf-8")
                            .sse(ssec)
                            .build()));
            s.note("SDK 错误: " + e.getMessage());
            s.note("Silo 服务端同样返回: Requests specifying SSE-C must be made over a secure connection");
            s.note("启用 TLS 后，把 MINIO_ENDPOINT / learn.minio.endpoint 改为 https://... 再运行本主题");
            s.cleanupBucket(bucket);
            return;
        }

        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        s.client().putObject(PutObjectArgs.builder()
                .bucket(bucket).object(key)
                .stream(new ByteArrayInputStream(body), body.length, -1)
                .contentType("text/plain; charset=utf-8")
                .sse(ssec)
                .build());
        s.ok("使用 SSE-C 写入对象");

        try (GetObjectResponse response = s.client().getObject(GetObjectArgs.builder()
                .bucket(bucket).object(key).ssec(ssec).build())) {
            s.checkEquals(text, new String(response.readAllBytes(), StandardCharsets.UTF_8),
                    "携带相同密钥可以解密读取");
        }
        s.checkEquals((long) body.length, s.client().statObject(StatObjectArgs.builder()
                        .bucket(bucket).object(key).ssec(ssec).build()).size(),
                "携带密钥 stat 成功");

        Exception e = s.expectException("不携带 SSE-C 密钥读取", ErrorResponseException.class,
                () -> s.client().getObject(GetObjectArgs.builder()
                        .bucket(bucket).object(key).build()).close());
        s.note("服务端错误: " + s.errorCode(e));
        if (e instanceof ErrorResponseException ere && ere.response() != null) {
            s.checkEquals(400, ere.response().code(), "缺少 SSE-C 密钥时返回 HTTP 400");
        }
        s.cleanupBucket(bucket);
    }

    private boolean isHttps(DemoSupport s) {
        return s.properties().endpoint() != null
                && s.properties().endpoint().regionMatches(true, 0, "https://", 0, 8);
    }
}
