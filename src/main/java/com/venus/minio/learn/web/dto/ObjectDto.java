package com.venus.minio.learn.web.dto;

import java.time.ZonedDateTime;

public record ObjectDto(
        String key,
        long size,
        String etag,
        ZonedDateTime lastModified,
        String versionId,
        boolean latest) {
}
