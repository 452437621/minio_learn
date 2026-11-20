package com.venus.minio.learn.web;

import java.time.Instant;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.venus.minio.learn.service.StorageService;
import com.venus.minio.learn.web.dto.PresignRequest;
import com.venus.minio.learn.web.dto.PresignResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/presign")
public class PresignController {

    private final StorageService storage;

    public PresignController(StorageService storage) {
        this.storage = storage;
    }

    @PostMapping
    public PresignResponse presign(@Valid @RequestBody PresignRequest request) throws Exception {
        String url = storage.presign(request.bucket(), request.key(),
                request.method(), request.expirySeconds());
        return new PresignResponse(url, Instant.now().plusSeconds(request.expirySeconds()));
    }
}
