package com.venus.minio.learn.web.dto;

import io.minio.http.Method;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PresignRequest(
        @NotBlank String bucket,
        @NotBlank String key,
        @NotNull Method method,
        @Min(1) @Max(604800) int expirySeconds) {
}
