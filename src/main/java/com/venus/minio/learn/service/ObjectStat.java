package com.venus.minio.learn.service;

import java.time.ZonedDateTime;
import java.util.Map;

public record ObjectStat(
        String bucket,
        String key,
        long size,
        String etag,
        String contentType,
        ZonedDateTime lastModified,
        String versionId,
        Map<String, String> userMetadata) {
}
