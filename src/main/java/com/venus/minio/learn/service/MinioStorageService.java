package com.venus.minio.learn.service;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.venus.minio.learn.web.dto.BucketDto;
import com.venus.minio.learn.web.dto.ObjectDto;
import com.venus.minio.learn.web.dto.UploadResult;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import io.minio.messages.Item;

@Service
public class MinioStorageService implements StorageService {

    private static final int PRESIGN_MAX_SECONDS = 7 * 24 * 60 * 60;

    private final MinioClient client;

    public MinioStorageService(MinioClient client) {
        this.client = client;
    }

    @Override
    public List<BucketDto> listBuckets() throws Exception {
        List<BucketDto> buckets = new ArrayList<>();
        for (var bucket : client.listBuckets()) {
            buckets.add(new BucketDto(bucket.name(), bucket.creationDate()));
        }
        return buckets;
    }

    @Override
    public void createBucket(String name) throws Exception {
        client.makeBucket(MakeBucketArgs.builder().bucket(name).build());
    }

    @Override
    public void deleteBucket(String name) throws Exception {
        client.removeBucket(RemoveBucketArgs.builder().bucket(name).build());
    }

    @Override
    public List<ObjectDto> listObjects(String bucket, String prefix, boolean recursive) throws Exception {
        List<ObjectDto> objects = new ArrayList<>();
        for (Result<Item> result : client.listObjects(ListObjectsArgs.builder()
                .bucket(bucket)
                .prefix(prefix == null ? "" : prefix)
                .recursive(recursive)
                .build())) {
            Item item = result.get();
            if (item.isDir() || item.isDeleteMarker()) {
                continue;
            }
            objects.add(new ObjectDto(item.objectName(), item.size(), item.etag(),
                    item.lastModified(), item.versionId(), item.isLatest()));
        }
        return objects;
    }

    @Override
    public ObjectStat stat(String bucket, String key) throws Exception {
        StatObjectResponse stat = client.statObject(StatObjectArgs.builder()
                .bucket(bucket).object(key).build());
        return new ObjectStat(bucket, key, stat.size(), stat.etag(), stat.contentType(),
                stat.lastModified(), stat.versionId(), Map.copyOf(stat.userMetadata()));
    }

    @Override
    public UploadResult upload(String bucket, String key, InputStream in, long size, String contentType)
            throws Exception {
        String effectiveType = (contentType == null || contentType.isBlank())
                ? "application/octet-stream" : contentType;
        var response = client.putObject(PutObjectArgs.builder()
                .bucket(bucket).object(key)
                .stream(in, size, -1)
                .contentType(effectiveType)
                .build());
        return new UploadResult(bucket, key, response.etag(), response.versionId(), size);
    }

    @Override
    public void download(String bucket, String key, OutputStream out) throws Exception {
        try (GetObjectResponse response = client.getObject(GetObjectArgs.builder()
                .bucket(bucket).object(key).build())) {
            response.transferTo(out);
        }
    }

    @Override
    public void removeObject(String bucket, String key, String versionId) throws Exception {
        RemoveObjectArgs.Builder builder = RemoveObjectArgs.builder().bucket(bucket).object(key);
        if (versionId != null && !versionId.isBlank()) {
            builder.versionId(versionId);
        }
        client.removeObject(builder.build());
    }

    @Override
    public String presign(String bucket, String key, Method method, int expirySeconds) throws Exception {
        if (expirySeconds < 1 || expirySeconds > PRESIGN_MAX_SECONDS) {
            throw new IllegalArgumentException("预签名有效期必须在 1 到 " + PRESIGN_MAX_SECONDS + " 秒之间");
        }
        return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(method)
                .bucket(bucket)
                .object(key)
                .expiry(expirySeconds)
                .build());
    }
}
