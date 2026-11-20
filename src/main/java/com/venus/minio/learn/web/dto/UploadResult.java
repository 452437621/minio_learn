package com.venus.minio.learn.web.dto;

public record UploadResult(String bucket, String key, String etag, String versionId, long size) {
}
