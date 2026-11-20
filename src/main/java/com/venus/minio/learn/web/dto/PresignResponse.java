package com.venus.minio.learn.web.dto;

import java.time.Instant;

public record PresignResponse(String url, Instant expiresAt) {
}
