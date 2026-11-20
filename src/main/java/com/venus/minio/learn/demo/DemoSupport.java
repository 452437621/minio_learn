package com.venus.minio.learn.demo;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.venus.minio.learn.config.MinioProperties;

import io.minio.BucketExistsArgs;
import io.minio.DisableObjectLegalHoldArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;

/**
 * 演示共享层：断言、打印、桶清理、对象读写、HTTP 验证。
 */
public class DemoSupport {

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    private final MinioClient client;
    private final MinioProperties properties;
    private final boolean keep;
    private int checks;
    private int expectedErrors;

    public DemoSupport(MinioClient client, MinioProperties properties, boolean keep) {
        this.client = client;
        this.properties = properties;
        this.keep = keep;
    }

    public MinioClient client() {
        return client;
    }

    public MinioProperties properties() {
        return properties;
    }

    public boolean keep() {
        return keep;
    }

    public int checks() {
        return checks;
    }

    public int expectedErrors() {
        return expectedErrors;
    }

    // ---------------------------------------------------------------- 输出

    public void header(String title) {
        System.out.println();
        System.out.println("============================================================");
        System.out.println("== " + title);
        System.out.println("============================================================");
    }

    public void step(String message) {
        System.out.println(" - " + message);
    }

    public void note(String message) {
        System.out.println("   [INFO] " + message);
    }

    public void ok(String message) {
        checks++;
        System.out.println("   [OK] " + message);
    }

    public void expected(String message) {
        expectedErrors++;
        System.out.println("   [EXPECTED] " + message);
    }

    public void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("断言失败: " + message);
        }
        ok(message);
    }

    public void checkEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new IllegalStateException(
                    "断言失败: " + message + "，期望 [" + expected + "]，实际 [" + actual + "]");
        }
        ok(message);
    }

    public Exception expectException(String label, Class<? extends Exception> type,
            ThrowingRunnable action) throws Exception {
        try {
            action.run();
        } catch (Exception e) {
            if (!type.isInstance(e)) {
                throw new IllegalStateException(label + " 的异常类型不符：期望 "
                        + type.getSimpleName() + "，实际 " + e.getClass().getSimpleName()
                        + "（" + e.getMessage() + "）", e);
            }
            expected(label + " -> " + e.getClass().getSimpleName());
            return e;
        }
        throw new IllegalStateException(label + " 期望抛出 " + type.getSimpleName() + "，但没有抛出");
    }

    public String errorCode(Exception e) {
        if (e instanceof ErrorResponseException ere) {
            int httpStatus = ere.response() == null ? -1 : ere.response().code();
            return ere.errorResponse().code() + " / HTTP " + httpStatus;
        }
        return e.getClass().getSimpleName();
    }

    public void checkStatus(String label, int expectedStatus, int actualStatus) {
        checkEquals(expectedStatus, actualStatus, label);
    }

    // ---------------------------------------------------------------- 桶

    public boolean bucketExists(String bucket) throws Exception {
        return client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
    }

    /** 删除旧桶后新建一个普通桶。 */
    public void createFreshBucket(String bucket) throws Exception {
        deleteBucketFully(bucket);
        client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        ok("创建桶 " + bucket);
    }

    /** 对象锁必须在建桶时开启，因此使用独立桶并先彻底删除旧桶。 */
    public void createFreshObjectLockBucket(String bucket) throws Exception {
        deleteBucketFully(bucket);
        client.makeBucket(MakeBucketArgs.builder().bucket(bucket).objectLock(true).build());
        ok("创建开启对象锁的桶 " + bucket);
    }

    /**
     * 彻底删除桶：按版本删除所有对象（包括删除标记），清理 legal hold，
     * GOVERNANCE 保留期通过 bypassGovernanceMode 绕过。
     */
    public void deleteBucketFully(String bucket) throws Exception {
        if (!bucketExists(bucket)) {
            return;
        }
        purgeBucket(bucket);
        client.removeBucket(RemoveBucketArgs.builder().bucket(bucket).build());
    }

    public void purgeBucket(String bucket) throws Exception {
        if (!bucketExists(bucket)) {
            return;
        }
        List<DeleteObject> deletions = new ArrayList<>();
        for (Result<Item> result : client.listObjects(ListObjectsArgs.builder()
                .bucket(bucket).recursive(true).includeVersions(true).build())) {
            Item item = result.get();
            try {
                client.disableObjectLegalHold(DisableObjectLegalHoldArgs.builder()
                        .bucket(bucket).object(item.objectName()).versionId(item.versionId()).build());
            } catch (Exception ignored) {
                // 普通桶没有 legal hold，忽略
            }
            deletions.add(item.versionId() == null
                    ? new DeleteObject(item.objectName())
                    : new DeleteObject(item.objectName(), item.versionId()));
        }

        for (int i = 0; i < deletions.size(); i += 1000) {
            List<DeleteObject> batch = new ArrayList<>(
                    deletions.subList(i, Math.min(i + 1000, deletions.size())));
            Iterable<Result<DeleteError>> results = client.removeObjects(RemoveObjectsArgs.builder()
                    .bucket(bucket).objects(batch).bypassGovernanceMode(true).build());
            for (Result<DeleteError> result : results) {
                try {
                    result.get();
                } catch (ErrorResponseException ignored) {
                    // NoSuchVersion 等并发清理错误可忽略
                }
            }
        }
    }

    /** 演示结束清理；--keep=true 时保留现场并打印 mcli 观察/清理命令。 */
    public void cleanupBucket(String bucket) throws Exception {
        if (keep) {
            note("按 --keep=true 保留桶 " + bucket + "，可用 mcli 观察：");
            System.out.println("     mcli ls --recursive --versions silo/" + bucket);
            System.out.println("     mcli rm --recursive --force --versions silo/" + bucket);
            System.out.println("     mcli rb --force silo/" + bucket);
        } else {
            deleteBucketFully(bucket);
            note("已清理桶 " + bucket);
        }
    }

    // ---------------------------------------------------------------- 对象

    public void putText(String bucket, String object, String text, String contentType) throws Exception {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        client.putObject(PutObjectArgs.builder()
                .bucket(bucket).object(object)
                .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                .contentType(contentType)
                .build());
    }

    public String getText(String bucket, String object) throws Exception {
        try (GetObjectResponse response = client.getObject(GetObjectArgs.builder()
                .bucket(bucket).object(object).build())) {
            return new String(response.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public StatObjectResponse stat(String bucket, String object) throws Exception {
        return client.statObject(StatObjectArgs.builder().bucket(bucket).object(object).build());
    }

    public List<Item> listItems(String bucket, String prefix, boolean includeVersions) throws Exception {
        List<Item> items = new ArrayList<>();
        for (Result<Item> result : client.listObjects(ListObjectsArgs.builder()
                .bucket(bucket).prefix(prefix).recursive(true)
                .includeVersions(includeVersions).build())) {
            items.add(result.get());
        }
        return items;
    }

    public List<Item> listItems(String bucket, String prefix) throws Exception {
        return listItems(bucket, prefix, false);
    }

    public Map<String, String> tags(String bucket, String object) throws Exception {
        return client.getObjectTags(io.minio.GetObjectTagsArgs.builder()
                .bucket(bucket).object(object).build()).get();
    }

    // ---------------------------------------------------------------- HTTP

    public HttpResponse<String> httpGet(String url) throws Exception {
        return HttpSupport.get(url);
    }

    public HttpResponse<String> httpPut(String url, byte[] body, String contentType) throws Exception {
        return HttpSupport.put(url, body, contentType);
    }

    public int httpStatus(String url) throws Exception {
        return httpGet(url).statusCode();
    }

    public String objectUrl(String bucket, String object) {
        return properties.endpoint().replaceAll("/+$", "") + "/" + bucket + "/" + object;
    }

    /** 读取对象前 n 个字节，用于验证 Range GET。 */
    public byte[] readRange(String bucket, String object, long offset, long length) throws Exception {
        try (InputStream in = client.getObject(GetObjectArgs.builder()
                .bucket(bucket).object(object).offset(offset).length(length).build())) {
            return in.readAllBytes();
        }
    }
}
