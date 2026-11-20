# MinIO / Silo 学习项目

一套面向 Java 开发者的对象存储学习工程：**中文教程 + 10 个可运行 Demo + Spring Boot Web 文件服务 + 集成测试**。

本机运行的是 **Silo**——PGSTY 维护的 MinIO 社区分支，S3 协议、数据格式、`MINIO_*` 环境变量和 `mc` 生态与 MinIO 兼容。因此这些代码同样适用于 MinIO、AWS S3、阿里云 OSS 等 S3 兼容服务（只需改 endpoint）。

> 已在当前环境验证：Silo 20261112、S3 API `http://127.0.0.1:9000`、控制台 `http://127.0.0.1:9001`、凭据 `minioadmin / minioadmin`；10 个 Demo 与 9 个集成测试全部通过。

## 1. 快速开始

前置条件：

- Silo / MinIO 已启动，S3 API 监听 `127.0.0.1:9000`
- JDK 21（脚本默认使用 `D:\ProgramFiles\JAVA\graalvm-jdk-21.0.7`，可用 `JAVA21_HOME` 覆盖）
- Maven 3.9+（本项目使用 3.9.14）
- 不需要 Docker

```bat
cd /d E:\IdeaProjects\3学习笔记\minio_learn

:: 1. 检查服务、端口和凭据
scripts\check-server.bat

:: 2. 构建（脚本会自动把 JAVA_HOME 切到 JDK 21）
scripts\build.bat

:: 3. 查看/运行 Demo
scripts\demo.bat list
scripts\demo.bat bucket
scripts\demo.bat object
scripts\demo.bat all

:: 4. 启动 Web 文件服务：http://127.0.0.1:8080
scripts\run-web.bat

:: 5. 运行集成测试（需要本机 Silo）
scripts\test-it.bat
```

> 为什么必须用脚本：本机 Maven 默认运行在 JDK 8 上，而 Spring Boot 3 的 Maven 插件要求 JDK 17+。`scripts\env-java21.bat` 会临时把 `JAVA_HOME` 指向 JDK 21。IntelliJ 中请把 Project SDK 和 Maven Runner JRE 设为 JDK 21。

## 2. 技术栈

| 组件 | 版本 / 说明 |
|---|---|
| Java | 21 |
| Spring Boot | 3.5.16 |
| MinIO Java SDK | 8.6.0 |
| OkHttp | 5.1.0（显式引入 `okhttp-jvm`，原因见下文） |
| Maven | 3.9.14 |
| 前端 | 原生 HTML/CSS/JS，无 npm、无 CDN |
| 测试 | JUnit 5 + Spring Boot Test + Failsafe |

`pom.xml` 中显式加入：

```xml
<dependency>
  <groupId>com.squareup.okhttp3</groupId>
  <artifactId>okhttp-jvm</artifactId>
  <version>5.1.0</version>
</dependency>
```

MinIO 8.6.0 的 Maven POM 引用的是 OkHttp 5 的 Kotlin 多平台门面 jar（`okhttp-5.1.0.jar` 本身只有元数据）；不补 `okhttp-jvm` 会出现 `Cannot access okhttp3.HttpUrl`。

## 3. Demo 主题

命令行入口：`scripts\demo.bat <topic> [--keep=true]`；`--keep=true` 会保留桶，便于打开控制台或 `mcli` 观察。

| 主题 | 内容 | 关键 API / 观察点 |
|---|---|---|
| `bucket` | 桶创建、列举、非空保护、删除 | `makeBucket`、`listBuckets`、`removeBucket`；非空桶返回 `BucketNotEmpty` |
| `object` | 上传/下载/stat/复制/Range/列举/删除 | `putObject`、`getObject`、`copyObject`、`statObject`、`listObjects` |
| `presign` | 预签名 GET/PUT，并用 HTTP 实际访问 | `getPresignedObjectUrl`；有效期最长 7 天 |
| `versioning` | 版本控制、历史版本、删除标记、挂起 | `setBucketVersioning`、`includeVersions(true)`、`versionId` |
| `lock` | 对象锁 GOVERNANCE、legal hold、bypass 删除 | 建桶时 `objectLock(true)`；保留期内普通删除被拒 |
| `lifecycle` | 过期、未完成分片清理、非当前版本过期 | `setBucketLifecycle`；Silo 会异步扫描，不立即删除 |
| `multipart` | 20 MiB 文件、5 MiB 分片、ETag 后缀 | `putObject(... stream(in, size, partSize))`；ETag 形如 `"...-4"` |
| `policy` | 匿名只读桶策略 | `setBucketPolicy`；设置后匿名 GET 200，删除策略后 403 |
| `ssec` | SSE-C 客户端密钥加密 | HTTP 下 SDK/Silo 都会拒绝；HTTPS 实验见 `docs/08` |
| `tagging` | 用户元数据与对象标签 | `userMetadata`、`get/set/deleteObjectTags` |

也可以直接在 IntelliJ 运行 `com.venus.minio.learn.Main`，Program arguments 填 `--demo=object`。

## 4. Web 文件服务

```bat
scripts\run-web.bat
```

打开 `http://127.0.0.1:8080`，支持：

- 选择/创建桶
- 上传文件（最大 100MB）
- 按前缀查询对象
- 下载、查看元数据、删除
- 生成预签名 GET/PUT URL（5 分钟 / 1 小时 / 1 天 / 7 天）

REST API：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/config` | endpoint、默认桶、控制台地址（不返回密钥） |
| GET | `/api/buckets` | 桶列表 |
| POST | `/api/buckets` | `{"name":"demo-bucket"}` |
| DELETE | `/api/buckets?name=` | 删除空桶 |
| GET | `/api/objects?bucket=&prefix=&recursive=true` | 对象列表 |
| POST | `/api/objects` | multipart：`bucket`、可选 `key`、`file` |
| GET | `/api/objects/stat?bucket=&key=` | 元数据 |
| GET | `/api/objects/download?bucket=&key=` | 流式下载 |
| DELETE | `/api/objects?bucket=&key=&versionId=` | 删除对象/版本 |
| POST | `/api/presign` | `{"bucket","key","method":"GET|PUT","expirySeconds"}` |

浏览器直接 `fetch` 预签名 PUT 会触发对象存储 CORS，所以页面只展示 URL；PUT 请用 curl 验证（见 docs/06 实验 7）。

## 5. 配置

`src/main/resources/application.yml` 支持环境变量覆盖：

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `MINIO_ENDPOINT` | `http://127.0.0.1:9000` | **S3 API 端口，不是 9001** |
| `MINIO_ACCESS_KEY` | `minioadmin` | 访问密钥 |
| `MINIO_SECRET_KEY` | `minioadmin` | 秘密密钥 |
| `MINIO_BUCKET` | `learn-files` | Web 默认桶 |
| `MINIO_CONSOLE_URL` | `http://127.0.0.1:9001` | 仅用于页面展示 |
| `MINIO_AUTO_CREATE_BUCKET` | `true` | 启动时自动建默认桶 |
| `MINIO_FAIL_FAST` | `false` | 连接失败时是否阻止启动 |
| `SERVER_PORT` | `8080` | Web 端口 |

示例：

```bat
set MINIO_ENDPOINT=https://s3.example.com
set MINIO_ACCESS_KEY=your-key
set MINIO_SECRET_KEY=your-secret
scripts\run-web.bat
```

## 6. 集成测试

```bat
scripts\test-it.bat
```

- `scripts\test-it.bat` 设置 `MINIO_IT=true` 并执行 `mvn verify`
- 不设置 `MINIO_IT` 时，9 个 `*IT` 测试全部 **skipped**，普通构建不受影响
- 测试使用随机桶名（`it-...`），`@AfterEach` 按版本清理，包括对象锁桶
- 覆盖：桶/对象 CRUD、预签名、版本化、对象锁、生命周期、分片、桶策略、SSE-C 安全约束、Web API

## 7. 已知 Silo 兼容性差异（实测）

1. **端口**：S3 API 在 **9000**，控制台在 **9001**。SDK、预签名 URL、curl 都必须用 9000。
2. **生命周期 abort 规则**：Silo 拒绝仅含 `AbortIncompleteMultipartUpload` 的规则（`InvalidArgument`）；与 `Expiration` 合并后配置成功，但 `getBucketLifecycle` 不会返回 abort 动作。详见 docs/04。
3. **生命周期删除**：SDK 8.6.0 删除规则后 `getBucketLifecycle` 返回 `null`，而不是抛 `NoSuchLifecycleConfiguration`。
4. **对象锁删除**：GOVERNANCE 保留期内、不带 bypass 的删除返回 `InvalidRequest` / HTTP 400（不同版本可能是 403）。
5. **SSE-C 必须 HTTPS**：SDK 和 Silo 都会拒绝 HTTP 上的 SSE-C 请求。`ssec` Demo 在 HTTP 下演示安全拦截；完整加解密请按 docs/08 启动 TLS 实验实例（已实测通过）。
6. **multipart ETag**：形如 `"0cdaff...-4"`，带引号；比较前应去掉引号。
7. **预签名上限**：1 秒到 604800 秒（7 天），超过由 SDK 本地抛 `IllegalArgumentException`。
8. **版本控制挂起**：SUSPENDED 状态下写入的 `versionId` 为 `null`。

## 8. 文档索引

| 文档 | 内容 |
|---|---|
| [docs/00-文档索引.md](docs/00-文档索引.md) | 阅读顺序与命令速查 |
| [docs/01-对象存储与S3模型.md](docs/01-对象存储与S3模型.md) | 对象存储、S3、签名、纠删码、术语 |
| [docs/02-Silo控制台与mcli快速上手.md](docs/02-Silo控制台与mcli快速上手.md) | 控制台、mcli/mc 安装与命令 |
| [docs/03-Java-SDK使用指南.md](docs/03-Java-SDK使用指南.md) | SDK 初始化、API 映射、代码要点 |
| [docs/04-高级特性实战.md](docs/04-高级特性实战.md) | 版本、对象锁、生命周期、策略、加密、分片 |
| [docs/05-运维与生产实践.md](docs/05-运维与生产实践.md) | 分布式、TLS、IAM、监控、备份、升级 |
| [docs/06-学习路线与实验手册.md](docs/06-学习路线与实验手册.md) | 4 周路线 + 15 个实验 |
| [docs/07-常见问题与排错.md](docs/07-常见问题与排错.md) | 常见错误码与排查 |
| [docs/08-SSE-C-HTTPS实验.md](docs/08-SSE-C-HTTPS实验.md) | 临时 TLS Silo 实例，完整 SSE-C 读写 |

## 9. 安全提示

- 本项目使用 `minioadmin / minioadmin` 默认凭据，仅用于本地学习；生产必须更换强密码并使用 IAM 用户。
- `ssec` Demo 会打印随机密钥，仅用于教学；生产密钥必须托管在 KMS/密钥管理系统中。
- `policy` Demo 会临时开放匿名只读；演示结束会删除策略。若程序异常退出，请执行 `mcli anonymous set none silo/learn-policy`。
- 不要手工修改 Silo/MinIO 数据目录中的文件；所有清理都通过 S3 API 或 `mcli`。
- 自签名 TLS 证书仅用于本地实验，生产请使用受信任 CA 证书。
