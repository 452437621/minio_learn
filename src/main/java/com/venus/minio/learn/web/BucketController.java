package com.venus.minio.learn.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.venus.minio.learn.service.StorageService;
import com.venus.minio.learn.web.dto.BucketDto;
import com.venus.minio.learn.web.dto.CreateBucketRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/buckets")
public class BucketController {

    private final StorageService storage;

    public BucketController(StorageService storage) {
        this.storage = storage;
    }

    @GetMapping
    public List<BucketDto> list() throws Exception {
        return storage.listBuckets();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> create(@Valid @RequestBody CreateBucketRequest request) throws Exception {
        storage.createBucket(request.name());
        return Map.of("name", request.name());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestParam String name) throws Exception {
        storage.deleteBucket(name);
    }
}
