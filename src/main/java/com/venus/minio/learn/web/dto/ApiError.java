package com.venus.minio.learn.web.dto;

public record ApiError(String code, String message, String requestId) {
}
