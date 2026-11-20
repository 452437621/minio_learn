package com.venus.minio.learn.web;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.venus.minio.learn.service.ObjectStat;
import com.venus.minio.learn.service.StorageService;
import com.venus.minio.learn.util.ObjectKeys;
import com.venus.minio.learn.web.dto.ObjectDto;
import com.venus.minio.learn.web.dto.UploadResult;

@RestController
@RequestMapping("/api/objects")
public class ObjectController {

    private final StorageService storage;

    public ObjectController(StorageService storage) {
        this.storage = storage;
    }

    @GetMapping
    public List<ObjectDto> list(@RequestParam String bucket,
            @RequestParam(defaultValue = "") String prefix,
            @RequestParam(defaultValue = "true") boolean recursive) throws Exception {
        return storage.listObjects(bucket, prefix, recursive);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResult upload(@RequestParam String bucket,
            @RequestParam(required = false) String key,
            @RequestParam("file") MultipartFile file) throws Exception {
        String requested = (key == null || key.isBlank()) ? file.getOriginalFilename() : key;
        String objectKey = ObjectKeys.normalize(requested);
        try (InputStream in = file.getInputStream()) {
            return storage.upload(bucket, objectKey, in, file.getSize(), file.getContentType());
        }
    }

    @GetMapping("/stat")
    public ObjectStat stat(@RequestParam String bucket, @RequestParam String key) throws Exception {
        return storage.stat(bucket, key);
    }

    @GetMapping("/download")
    public ResponseEntity<StreamingResponseBody> download(@RequestParam String bucket,
            @RequestParam String key) throws Exception {
        ObjectStat stat = storage.stat(bucket, key);
        StreamingResponseBody body = out -> {
            try {
                storage.download(bucket, key, out);
            } catch (java.io.IOException e) {
                throw e;
            } catch (Exception e) {
                throw new java.io.IOException("下载对象失败: " + key, e);
            }
        };

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(ObjectKeys.fileName(key), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(parseMediaType(stat.contentType()))
                .contentLength(stat.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(body);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestParam String bucket,
            @RequestParam String key,
            @RequestParam(required = false) String versionId) throws Exception {
        storage.removeObject(bucket, key, versionId);
    }

    private MediaType parseMediaType(String value) {
        if (value == null || value.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(value);
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
