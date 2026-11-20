package com.venus.minio.learn.demo;

import java.io.IOException;
import java.net.http.HttpResponse;

/**
 * 演示用 HTTP 工具，用于验证预签名 URL、匿名桶策略等“绕过 SDK”的访问方式。
 */
public final class HttpSupport {

    private HttpSupport() {
    }

    public static HttpResponse<String> get(String url) throws IOException, InterruptedException {
        return HttpSupport.client().send(
                java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                        .timeout(java.time.Duration.ofSeconds(30))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static HttpResponse<String> put(String url, byte[] body, String contentType)
            throws IOException, InterruptedException {
        return HttpSupport.client().send(
                java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                        .timeout(java.time.Duration.ofSeconds(30))
                        .header("Content-Type", contentType)
                        .PUT(java.net.http.HttpRequest.BodyPublishers.ofByteArray(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static java.net.http.HttpClient client() {
        return java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .build();
    }
}
