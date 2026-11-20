package com.venus.minio.learn.web.dto;

import java.time.ZonedDateTime;

public record BucketDto(String name, ZonedDateTime creationDate) {
}
