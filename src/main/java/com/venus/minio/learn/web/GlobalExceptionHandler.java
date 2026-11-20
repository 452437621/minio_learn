package com.venus.minio.learn.web;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.venus.minio.learn.web.dto.ApiError;

import io.minio.errors.ErrorResponseException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ApiError> handleS3(ErrorResponseException e) {
        String code = e.errorResponse().code();
        int status = statusFor(code, e.response() == null ? 500 : e.response().code());
        log.warn("S3 请求失败: {} / HTTP {}", code, status);
        return ResponseEntity.status(status).body(new ApiError(
                code, e.errorResponse().message(), e.errorResponse().requestId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("参数校验失败");
        return ResponseEntity.badRequest().body(new ApiError("InvalidArgument", message, null));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiError("EntityTooLarge", "上传文件超过 100MB 限制", null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(new ApiError("InvalidArgument", e.getMessage(), null));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiError> handleIo(IOException e) {
        log.warn("对象存储 I/O 失败: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("ServiceUnavailable", "对象存储暂不可用", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnknown(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("InternalError", "服务器内部错误", null));
    }

    private int statusFor(String code, int fallback) {
        return switch (code) {
            case "NoSuchKey", "NoSuchBucket", "NoSuchVersion", "NoSuchUpload" -> 404;
            case "AccessDenied", "SignatureDoesNotMatch", "InvalidAccessKeyId",
                    "AllAccessDisabled", "InvalidToken", "ExpiredToken" -> 403;
            case "BucketAlreadyExists", "BucketAlreadyOwnedByYou", "BucketNotEmpty" -> 409;
            case "InvalidBucketName", "InvalidArgument", "InvalidRequest", "MalformedXML",
                    "InvalidPolicyDocument", "EntityTooLarge" -> 400;
            default -> fallback;
        };
    }
}
