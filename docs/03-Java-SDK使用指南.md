# 03 · Java SDK 使用指南

本篇与 `src/main/java/com/venus/minio/learn/demo/` 下的代码一一对应。

## 3.1 依赖

```xml
<properties>
  <java.version>21</java.version>
  <minio.version>8.6.0</minio.version>
</properties>

<dependency>
  <groupId>io.minio</groupId>
  <artifactId>minio</artifactId>
  <version>${minio.version}</version>
</dependency>

<!-- MinIO 8.6.0 的 POM 引用 OkHttp 5 的 Kotlin 多平台门面 jar，
     Maven 需要显式补上包含 JVM 类的 okhttp-jvm -->
<dependency>
  <groupId>com.squareup.okhttp3</groupId>
  <artifactId>okhttp-jvm</artifactId>
  <version>5.1.0</version>
</dependency>
```

构建环境必须是 JDK 17+（本项目用 21）。`scripts\env-java21.bat` 解决本机 Maven 默认 JDK 8 的问题。

## 3.2 创建客户端

```java
MinioClient client = MinioClient.builder()
        .endpoint("http://127.0.0.1:9000")   // S3 API，不是 9001
        .credentials("minioadmin", "minioadmin")
        .build();
```

生产建议：

- endpoint 使用 HTTPS。
- 凭据从环境变量/密钥管理系统读取，不要硬编码。
- 为客户端设置合理的连接/读取超时（SDK 默认值通常够用，长上传需单独配置）。
- 同一应用复用 `MinioClient`，它是线程安全的。

## 3.3 异常模型

| 异常 | 含义 | 处理 |
|---|---|---|
| `ErrorResponseException` | 服务端返回 S3 错误 XML | 读 `errorResponse().code()` / `response().code()` |
| `IOException` | 网络/流错误 | 重试、降级、告警 |
| `IllegalArgumentException` | 本地参数校验失败 | 修正参数（如预签名超 7 天、SSE-C 用 HTTP） |
| `InsufficientDataException` | 流长度与声明不符 | 检查 `stream(in, size, ...)` 的 size |
| `ServerException` / `InternalException` | 服务端 5xx | 有限重试，记录 requestId |

```java
try {
    client.statObject(StatObjectArgs.builder().bucket(b).object(k).build());
} catch (ErrorResponseException e) {
    System.out.println(e.errorResponse().code());       // NoSuchKey
    System.out.println(e.response().code());            // 404
    System.out.println(e.errorResponse().requestId());  // 排障用
}
```

## 3.4 Demo 与 API 映射

| Demo | 主要类/方法 |
|---|---|
| `bucket` | `makeBucket`、`listBuckets`、`bucketExists`、`removeBucket` |
| `object` | `putObject`、`getObject`、`statObject`、`copyObject`、`listObjects`、`removeObject` |
| `presign` | `getPresignedObjectUrl` |
| `versioning` | `setBucketVersioning`、`getBucketVersioning`、`listObjects(includeVersions)` |
| `lock` | `makeBucket(objectLock)`、`setObjectLockConfiguration`、`setObjectRetention`、`enable/disableObjectLegalHold` |
| `lifecycle` | `setBucketLifecycle`、`getBucketLifecycle`、`deleteBucketLifecycle` |
| `multipart` | `putObject(stream(in, size, partSize))` |
| `policy` | `setBucketPolicy`、`deleteBucketPolicy` |
| `ssec` | `ServerSideEncryptionCustomerKey` + `.sse()` / `.ssec()` |
| `tagging` | `userMetadata`、`get/set/deleteObjectTags` |

## 3.5 桶与对象

### 桶

```java
client.makeBucket(MakeBucketArgs.builder().bucket("learn-bucket").build());

boolean exists = client.bucketExists(
        BucketExistsArgs.builder().bucket("learn-bucket").build());

for (Bucket b : client.listBuckets()) {
    System.out.println(b.name() + " " + b.creationDate());
}

client.removeBucket(RemoveBucketArgs.builder().bucket("learn-bucket").build());
```

非空桶删除会返回 `BucketNotEmpty`（HTTP 409）。版本化桶还要先删除所有版本和删除标记。

### 上传

```java
byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
client.putObject(PutObjectArgs.builder()
        .bucket(bucket).object("docs/hello.txt")
        .stream(new ByteArrayInputStream(data), data.length, -1)
        .contentType("text/plain; charset=utf-8")
        .userMetadata(Map.of("author", "venus"))
        .tags(Map.of("env", "learn"))
        .build());
```

`stream(in, objectSize, partSize)`：

- `objectSize >= 0`、`partSize = -1`：SDK 自动决定是否分片（20 MiB 会按 5 MiB 分片）。
- `objectSize >= 0`、`partSize > 0`：显式指定分片大小，最小 5 MiB。
- `objectSize = -1`：流长度未知，必须显式给出 `partSize`，SDK 使用带符号的 chunked 上传。

### 下载、stat、Range

```java
try (GetObjectResponse response = client.getObject(GetObjectArgs.builder()
        .bucket(bucket).object("docs/hello.txt").build())) {
    byte[] bytes = response.readAllBytes();
}

StatObjectResponse stat = client.statObject(StatObjectArgs.builder()
        .bucket(bucket).object("docs/hello.txt").build());
System.out.println(stat.size() + " " + stat.etag() + " " + stat.contentType());

try (GetObjectResponse range = client.getObject(GetObjectArgs.builder()
        .bucket(bucket).object("docs/hello.txt")
        .offset(0L).length(5L).build())) {
    // 读取前 5 字节
}
```

### 复制、列举、删除

```java
client.copyObject(CopyObjectArgs.builder()
        .bucket(bucket).object("copy/hello.txt")
        .source(CopySource.builder().bucket(bucket).object("docs/hello.txt").build())
        .build());

for (Result<Item> result : client.listObjects(ListObjectsArgs.builder()
        .bucket(bucket).prefix("docs/").recursive(true).build())) {
    Item item = result.get();
    System.out.println(item.objectName() + " " + item.size());
}

client.removeObject(RemoveObjectArgs.builder()
        .bucket(bucket).object("docs/hello.txt").build());
```

## 3.6 预签名 URL

```java
String getUrl = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
        .method(Method.GET)
        .bucket(bucket).object("docs/hello.txt")
        .expiry(10, TimeUnit.MINUTES)
        .build());

String putUrl = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
        .method(Method.PUT)
        .bucket(bucket).object("uploads/a.txt")
        .expiry(1, TimeUnit.HOURS)
        .build());
```

- 有效期范围 1 秒 – 604800 秒（7 天），超限由 SDK 本地抛 `IllegalArgumentException`。
- 预签名 URL 使用 endpoint 生成；如果应用部署在反向代理后，必须配置对外可访问的 endpoint。
- PUT 预签名可以直接用 curl 上传：

```bat
curl -X PUT --data-binary "@README.md" "<presigned-put-url>"
```

## 3.7 版本控制

```java
client.setBucketVersioning(SetBucketVersioningArgs.builder().bucket(bucket)
        .config(new VersioningConfiguration(
                VersioningConfiguration.Status.ENABLED, null))
        .build());

VersioningConfiguration config = client.getBucketVersioning(
        GetBucketVersioningArgs.builder().bucket(bucket).build());

for (Result<Item> result : client.listObjects(ListObjectsArgs.builder()
        .bucket(bucket).recursive(true).includeVersions(true).build())) {
    Item item = result.get();
    System.out.println(item.objectName() + " " + item.versionId()
            + " latest=" + item.isLatest()
            + " deleteMarker=" + item.isDeleteMarker());
}

try (GetObjectResponse old = client.getObject(GetObjectArgs.builder()
        .bucket(bucket).object(key).versionId(oldVersionId).build())) {
    // 读取历史版本
}
```

- 删除（不带 versionId）在版本化桶中会生成 Delete Marker。
- 按 versionId 删除才会真正移除某个版本。
- `SUSPENDED` 状态下写入产生 `versionId = null` 的版本。
- 清理版本化桶：列出所有版本 → 按 versionId 批量删除 → 删除桶。

## 3.8 对象锁

```java
client.makeBucket(MakeBucketArgs.builder()
        .bucket(bucket).objectLock(true).build());   // 必须建桶时开启

client.setObjectLockConfiguration(SetObjectLockConfigurationArgs.builder()
        .bucket(bucket)
        .config(new ObjectLockConfiguration(
                RetentionMode.GOVERNANCE, new RetentionDurationDays(1)))
        .build());

client.setObjectRetention(SetObjectRetentionArgs.builder()
        .bucket(bucket).object(key).versionId(versionId)
        .config(new Retention(RetentionMode.GOVERNANCE,
                ZonedDateTime.now().plusMinutes(10)))
        .bypassGovernanceMode(false)
        .build());

client.enableObjectLegalHold(EnableObjectLegalHoldArgs.builder()
        .bucket(bucket).object(key).versionId(versionId).build());

client.removeObject(RemoveObjectArgs.builder()
        .bucket(bucket).object(key).versionId(versionId)
        .bypassGovernanceMode(true).build());
```

- 对象锁桶会自动开启版本控制。
- GOVERNANCE 可被有权限者通过 `bypassGovernanceMode(true)` 绕过；COMPLIANCE 到期前任何人都不能删除。
- Legal Hold 独立于保留期限，删除前必须先 `disableObjectLegalHold`。
- 对象锁开启后不能关闭；错误地给生产桶开启 COMPLIANCE 可能导致数据无法删除。

## 3.9 生命周期

```java
import io.minio.messages.Status;   // 注意：不是 VersioningConfiguration.Status

LifecycleRule expire = new LifecycleRule(
        Status.ENABLED, null,
        new Expiration((ZonedDateTime) null, 1, null),
        new RuleFilter("tmp/"), "expire-tmp-after-1-day", null, null, null);

LifecycleRule abortAndExpire = new LifecycleRule(
        Status.ENABLED,
        new AbortIncompleteMultipartUpload(1),
        new Expiration((ZonedDateTime) null, 30, null),
        new RuleFilter("uploads/"), "abort-and-expire", null, null, null);

client.setBucketLifecycle(SetBucketLifecycleArgs.builder().bucket(bucket)
        .config(new LifecycleConfiguration(List.of(expire, abortAndExpire)))
        .build());

LifecycleConfiguration config = client.getBucketLifecycle(
        GetBucketLifecycleArgs.builder().bucket(bucket).build());

client.deleteBucketLifecycle(
        DeleteBucketLifecycleArgs.builder().bucket(bucket).build());
```

Silo 实测差异：

- **abort-only 规则被拒绝**（`InvalidArgument`）；把 `AbortIncompleteMultipartUpload` 与 `Expiration` 放在同一规则可以通过配置校验。
- 即使配置成功，`getBucketLifecycle` 也不会返回 abort 动作（只保留 Expiration）。以实际版本行为为准。
- 删除后 `getBucketLifecycle` 返回 `null`（SDK 8.6.0），不是抛异常。
- 生命周期由后台 scanner 异步执行，配置成功不等于立即删除。

## 3.10 分片上传

```java
long size = 20L * 1024 * 1024;
long partSize = 5L * 1024 * 1024;

try (InputStream in = Files.newInputStream(file)) {
    ObjectWriteResponse response = client.putObject(PutObjectArgs.builder()
            .bucket(bucket).object("big/20m.bin")
            .stream(in, size, partSize)
            .contentType("application/octet-stream")
            .build());
    System.out.println(response.etag());   // 形如 "md5-4"
}
```

- 5 MiB 是最小分片（最后一片除外）；20 MiB / 5 MiB = 4 片。
- ETag 不是整个对象的 MD5，而是分片 MD5 的组合，带 `-N` 后缀。
- minio-java 8.6.0 没有公开的 `createMultipartUpload/uploadPart` 低层 API；需要手工控制分片时使用 AWS SDK v2。
- 失败的分片会残留为未完成上传；可用生命周期 `AbortIncompleteMultipartUpload` 清理。

## 3.11 桶策略

```java
String policy = """
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {"AWS": ["*"]},
    "Action": ["s3:GetObject"],
    "Resource": ["arn:aws:s3:::%s/*"]
  }]
}
""".formatted(bucket);

client.setBucketPolicy(SetBucketPolicyArgs.builder()
        .bucket(bucket).config(policy).build());

client.deleteBucketPolicy(DeleteBucketPolicyArgs.builder()
        .bucket(bucket).build());
```

- 只授予 `s3:GetObject` 不会允许匿名 LIST。
- 生产策略应限制前缀、来源 IP/VPC、TLS，并优先使用 IAM 用户而不是匿名。
- 删除策略后匿名访问立即恢复 403。

## 3.12 SSE-C 客户端加密

```java
byte[] rawKey = new byte[32];
new SecureRandom().nextBytes(rawKey);
SecretKey key = new SecretKeySpec(rawKey, "AES");
ServerSideEncryptionCustomerKey ssec = new ServerSideEncryptionCustomerKey(key);

client.putObject(PutObjectArgs.builder()
        .bucket(bucket).object(keyName)
        .stream(in, size, -1)
        .sse(ssec)          // 写：sse
        .build());

try (GetObjectResponse response = client.getObject(GetObjectArgs.builder()
        .bucket(bucket).object(keyName)
        .ssec(ssec)         // 读：ssec
        .build())) {
    // ...
}
```

- 密钥必须是 32 字节 AES 密钥。
- **必须 HTTPS**：SDK 在本地就抛 `IllegalArgumentException`；Silo 服务端也会返回 400。
- 密钥丢失后数据不可恢复；生产必须使用 KMS/密钥托管，并启用 TLS。
- 完整 HTTPS 实验见 [08-SSE-C-HTTPS实验](08-SSE-C-HTTPS实验.md)。

## 3.13 元数据与标签

```java
client.putObject(PutObjectArgs.builder()
        .bucket(bucket).object(key)
        .stream(in, size, -1)
        .userMetadata(Map.of("author", "venus"))
        .tags(Map.of("env", "learn"))
        .build());

Map<String, String> tags = client.getObjectTags(GetObjectTagsArgs.builder()
        .bucket(bucket).object(key).build()).get();

client.setObjectTags(SetObjectTagsArgs.builder()
        .bucket(bucket).object(key)
        .tags(Map.of("env", "prod", "owner", "team-a"))
        .build());

client.deleteObjectTags(DeleteObjectTagsArgs.builder()
        .bucket(bucket).object(key).build());
```

- User Metadata 适合业务属性，写入后随对象保存。
- Object Tags 最多 10 个，适合生命周期/权限/成本规则。
- 标签更新是覆盖式的：`setObjectTags` 会替换全部标签。

## 3.14 清理版本化/对象锁桶

```java
List<DeleteObject> deletions = new ArrayList<>();
for (Result<Item> r : client.listObjects(ListObjectsArgs.builder()
        .bucket(bucket).recursive(true).includeVersions(true).build())) {
    Item item = r.get();
    if (item.versionId() == null) {
        deletions.add(new DeleteObject(item.objectName()));
    } else {
        deletions.add(new DeleteObject(item.objectName(), item.versionId()));
    }
}

for (Result<DeleteError> r : client.removeObjects(RemoveObjectsArgs.builder()
        .bucket(bucket).objects(deletions)
        .bypassGovernanceMode(true)   // GOVERNANCE 保留期可绕过
        .build())) {
    r.get();
}

client.removeBucket(RemoveBucketArgs.builder().bucket(bucket).build());
```

Legal Hold 需要先对每个版本执行 `disableObjectLegalHold`；COMPLIANCE 模式只能等到期。

## 3.15 测试建议

- 集成测试用 `@EnabledIfEnvironmentVariable(named = "MINIO_IT", matches = "true")` 门控，避免没有服务时构建失败。
- 每个测试创建唯一桶（如 `it-<时间>-<随机>`），`@AfterEach` 清理。
- 断言服务端行为时不要过度依赖错误消息文本；优先断言错误码/HTTP 状态。
- 对异步语义（生命周期扫描、复制）不要做“立即生效”的断言。

## 3.16 常见 SDK 坑

1. endpoint 写成 9001（控制台）→ 返回 HTML，解析失败。
2. 忘加 `okhttp-jvm` → `Cannot access okhttp3.HttpUrl`。
3. 用 `enableVersioning` → 8.x 没有这个方法，用 `setBucketVersioning`。
4. 用 `sseCustomerKey(String)` → 8.x 必须构造 `ServerSideEncryptionCustomerKey`。
5. 生命周期 `Status` 导错包 → 编译失败或序列化异常。
6. 预签名超过 7 天 → 本地 `IllegalArgumentException`。
7. SSE-C 在 HTTP 上 → 本地/服务端双重拒绝。
8. ETag 带引号 → 比较前 `replace("\"", "")`。
9. 版本化桶直接删桶 → `BucketNotEmpty`。
10. 对象锁建桶时没开 → 事后无法补开。
