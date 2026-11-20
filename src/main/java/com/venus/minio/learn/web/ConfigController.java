package com.venus.minio.learn.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.venus.minio.learn.config.MinioProperties;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final MinioProperties properties;

    public ConfigController(MinioProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> config() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("endpoint", properties.endpoint());
        result.put("bucket", properties.bucket());
        result.put("consoleUrl", properties.consoleUrl());
        result.put("presignMaxSeconds", 604800);
        return result;
    }
}
