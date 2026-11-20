package com.venus.minio.learn.service;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import com.venus.minio.learn.web.dto.BucketDto;
import com.venus.minio.learn.web.dto.ObjectDto;
import com.venus.minio.learn.web.dto.UploadResult;

import io.minio.http.Method;

/**
 * Web 层使用的对象存储门面，屏蔽 MinIO SDK 的响应类型。
 */
public interface StorageService {

    List<BucketDto> listBuckets() throws Exception;

    void createBucket(String name) throws Exception;

    void deleteBucket(String name) throws Exception;

    List<ObjectDto> listObjects(String bucket, String prefix, boolean recursive) throws Exception;

    ObjectStat stat(String bucket, String key) throws Exception;

    UploadResult upload(String bucket, String key, InputStream in, long size, String contentType)
            throws Exception;

    void download(String bucket, String key, OutputStream out) throws Exception;

    void removeObject(String bucket, String key, String versionId) throws Exception;

    String presign(String bucket, String key, Method method, int expirySeconds) throws Exception;
}
