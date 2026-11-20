package com.venus.minio.learn.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateBucketRequest(
        @NotBlank
        @Pattern(regexp = "^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$",
                message = "桶名只能包含小写字母、数字、点和连字符，长度 3-63")
        String name) {
}
