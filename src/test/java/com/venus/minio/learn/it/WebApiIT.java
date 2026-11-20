package com.venus.minio.learn.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.venus.minio.learn.web.dto.ObjectDto;
import com.venus.minio.learn.web.dto.PresignRequest;
import com.venus.minio.learn.web.dto.PresignResponse;
import com.venus.minio.learn.web.dto.UploadResult;

import io.minio.http.Method;

@EnabledIfEnvironmentVariable(named = "MINIO_IT", matches = "true")
class WebApiIT extends MinioItBase {

    @Autowired
    private TestRestTemplate rest;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void uploadListDownloadPresignDelete() throws Exception {
        String bucket = newBucket("it-web");
        byte[] content = "web api content".getBytes(StandardCharsets.UTF_8);

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("bucket", bucket);
        form.add("key", "web/hello.txt");
        form.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "hello.txt";
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<UploadResult> upload = rest.postForEntity(
                "/api/objects", new HttpEntity<>(form, headers), UploadResult.class);
        assertThat(upload.getStatusCode().value()).isEqualTo(201);
        assertThat(upload.getBody()).isNotNull();
        assertThat(upload.getBody().key()).isEqualTo("web/hello.txt");
        assertThat(upload.getBody().size()).isEqualTo(content.length);

        ResponseEntity<ObjectDto[]> list = rest.getForEntity(
                "/api/objects?bucket={bucket}&prefix=web/&recursive=true", ObjectDto[].class, bucket);
        assertThat(list.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(list.getBody()).isNotNull();
        assertThat(List.of(list.getBody())).extracting(ObjectDto::key).contains("web/hello.txt");

        ResponseEntity<byte[]> download = rest.getForEntity(
                "/api/objects/download?bucket={bucket}&key={key}", byte[].class, bucket, "web/hello.txt");
        assertThat(download.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(download.getBody()).isEqualTo(content);

        ResponseEntity<Map> stat = rest.getForEntity(
                "/api/objects/stat?bucket={bucket}&key={key}", Map.class, bucket, "web/hello.txt");
        assertThat(stat.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(stat.getBody()).containsKey("size");

        PresignRequest presignRequest = new PresignRequest(bucket, "web/hello.txt", Method.GET, 600);
        ResponseEntity<PresignResponse> presign = rest.postForEntity(
                "/api/presign", presignRequest, PresignResponse.class);
        assertThat(presign.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(presign.getBody()).isNotNull();
        HttpResponse<String> presignedGet = http.send(
                HttpRequest.newBuilder(URI.create(presign.getBody().url())).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(presignedGet.statusCode()).isEqualTo(200);
        assertThat(presignedGet.body()).isEqualTo("web api content");

        ResponseEntity<Void> deleted = rest.exchange(
                "/api/objects?bucket={bucket}&key={key}", HttpMethod.DELETE, HttpEntity.EMPTY,
                Void.class, bucket, "web/hello.txt");
        assertThat(deleted.getStatusCode().value()).isEqualTo(204);

        ResponseEntity<Map> config = rest.getForEntity("/api/config", Map.class);
        assertThat(config.getBody()).containsKeys("endpoint", "bucket", "consoleUrl");
    }
}
