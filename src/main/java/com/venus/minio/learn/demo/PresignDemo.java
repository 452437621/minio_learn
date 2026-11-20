package com.venus.minio.learn.demo;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.StatObjectArgs;
import io.minio.http.Method;

@Component
public class PresignDemo implements Demo {

    @Override
    public String name() {
        return "presign";
    }

    @Override
    public String description() {
        return "预签名 GET/PUT URL、HTTP 实际验证与 7 天上限";
    }

    @Override
    public void run(DemoSupport s) throws Exception {
        s.header("PresignDemo - 临时授权访问");
        String bucket = "learn-presign";
        String key = "reports/hello.txt";
        s.createFreshBucket(bucket);
        s.putText(bucket, key, "presigned content v1", "text/plain; charset=utf-8");

        String getUrl = s.client().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET).bucket(bucket).object(key)
                .expiry(10, TimeUnit.MINUTES).build());
        s.note("GET URL 前 120 字符: " + getUrl.substring(0, Math.min(120, getUrl.length())) + "...");
        HttpResponse<String> getResponse = s.httpGet(getUrl);
        s.checkStatus("预签名 GET 可匿名访问", 200, getResponse.statusCode());
        s.checkEquals("presigned content v1", getResponse.body(), "预签名 GET 返回正确内容");

        String putKey = "uploads/from-presigned-put.txt";
        String putUrl = s.client().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.PUT).bucket(bucket).object(putKey)
                .expiry(10, TimeUnit.MINUTES).build());
        byte[] body = "uploaded via presigned URL".getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> putResponse = s.httpPut(putUrl, body, "text/plain; charset=utf-8");
        s.checkStatus("预签名 PUT 可匿名上传", 200, putResponse.statusCode());
        s.checkEquals((long) body.length,
                s.client().statObject(StatObjectArgs.builder().bucket(bucket).object(putKey).build()).size(),
                "预签名 PUT 后对象大小正确");

        Exception e = s.expectException("申请 8 天有效期的预签名 URL", IllegalArgumentException.class,
                () -> s.client().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET).bucket(bucket).object(key)
                        .expiry(8, TimeUnit.DAYS).build()));
        s.note("SDK 本地限制: " + e.getMessage());
        s.cleanupBucket(bucket);
    }
}
