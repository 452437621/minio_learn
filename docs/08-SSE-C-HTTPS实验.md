# 08 · SSE-C HTTPS 实验

## 8.1 为什么需要这个实验

SSE-C（Server-Side Encryption with Customer-Provided Keys）要求客户端在每次 PUT/GET/HEAD 时携带 32 字节 AES 密钥。S3 规范要求密钥传输必须加密，因此：

- MinIO Java SDK 在 HTTP endpoint 上直接抛 `IllegalArgumentException`。
- Silo 服务端对 HTTP 上的 SSE-C 请求返回 400 `InvalidRequest`。

本机正式实例是 `http://127.0.0.1:9000`。为了完整跑通 SSE-C，我们启动一个**临时 TLS 实例**（9443），使用独立数据目录，不修改也不影响 9000 上的正式实例。

> 本实验已在当前环境验证通过：TLS 健康检查 200，SSE-C 加密写入/解密读取/stat 成功，无 key 读取 400。

## 8.2 准备

要求：

- 已执行 `scripts\build.bat`
- Silo 二进制：`D:\ProgramFiles\silo_20261112000000.0.0_windows_amd64\silo.exe`
- JDK 21：`D:\ProgramFiles\JAVA\graalvm-jdk-21.0.7`
- 端口 9443/9444 未被占用
- `openssl` 可用（Git Bash 自带）

所有命令在项目根目录执行：

```bat
cd /d E:\IdeaProjects\3学习笔记\minio_learn
```

## 8.3 生成自签名证书和 truststore

Git Bash：

```bash
mkdir -p target/tls/certs target/tls/data

MSYS_NO_PATHCONV=1 openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout target/tls/certs/private.key \
  -out target/tls/certs/public.crt \
  -days 2 \
  -subj "/CN=127.0.0.1" \
  -addext "subjectAltName=IP:127.0.0.1"
```

生成 JVM truststore（cmd 或 Git Bash 均可）：

```bat
"D:\ProgramFiles\JAVA\graalvm-jdk-21.0.7\bin\keytool" ^
  -importcert -noprompt -alias silo-local ^
  -file target\tls\certs\public.crt ^
  -keystore target\tls\truststore.p12 ^
  -storetype PKCS12 -storepass changeit
```

要点：

- `private.key` 和 `public.crt` 是 Silo 的 `--certs-dir` 要求的文件名。
- `subjectAltName=IP:127.0.0.1` 必须包含，否则 Java 会报 “No subject alternative names matching IP address 127.0.0.1”。
- 自签名证书仅用于本地实验；生产使用受信任 CA。

## 8.4 启动临时 TLS Silo

另开一个终端：

```bat
cd /d E:\IdeaProjects\3学习笔记\minio_learn

set SILO_HOME=D:\ProgramFiles\silo_20261112000000.0.0_windows_amd64

"%SILO_HOME%\silo.exe" server "%CD%\target\tls\data" ^
  --address :9443 ^
  --console-address :9444 ^
  --certs-dir "%CD%\target\tls\certs"
```

参数说明：

| 参数 | 含义 |
|---|---|
| `%CD%\target\tls\data` | 独立数据目录，不要指向 9000 实例的数据目录 |
| `--address :9443` | S3 API 监听 9443（自动启用 TLS） |
| `--console-address :9444` | 控制台监听 9444 |
| `--certs-dir ...` | 包含 `public.crt` 和 `private.key` 的目录 |

启动成功后日志会显示 `API: https://127.0.0.1:9443`、`WebUI: https://127.0.0.1:9444`。

验证：

```bat
curl -k -s -o NUL -w "%{http_code}" https://127.0.0.1:9443/minio/health/live
```

期望 `200`。`-k` 表示临时接受自签名证书（仅本实验）。

## 8.5 运行 SSE-C Demo

在项目根目录：

```bat
set JAVA_HOME=D:\ProgramFiles\JAVA\graalvm-jdk-21.0.7

"%JAVA_HOME%\bin\java" ^
  -Djavax.net.ssl.trustStore="%CD%\target\tls\truststore.p12" ^
  -Djavax.net.ssl.trustStorePassword=changeit ^
  -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 ^
  -jar target\minio-learn-0.0.1-SNAPSHOT.jar ^
  --demo=ssec ^
  --learn.minio.endpoint=https://127.0.0.1:9443
```

预期输出（节选）：

```text
== SseCDemo - 服务端加密（客户提供密钥）
   [OK] 创建桶 learn-ssec
   [INFO] 本次演示密钥（Base64，仅学习用，切勿用于生产）: ...
   [OK] 使用 SSE-C 写入对象
   [OK] 携带相同密钥可以解密读取
   [OK] 携带密钥 stat 成功
   [EXPECTED] 不携带 SSE-C 密钥读取 -> ErrorResponseException
   [INFO] 服务端错误: InvalidRequest / HTTP 400
   [OK] 缺少 SSE-C 密钥时返回 HTTP 400
   [INFO] 已清理桶 learn-ssec
== ssec done: 5 checks, 1 expected errors
```

关键点：

- `--learn.minio.endpoint=https://127.0.0.1:9443` 覆盖 `application.yml` 的 HTTP endpoint。
- `-Djavax.net.ssl.trustStore` 让 JVM 信任自签名证书；OkHttp 使用 JVM 默认 truststore。
- Demo 会创建独立桶、加密写入、解密读取、验证无 key 失败，最后清理。

## 8.6 停止与清理

1. 在运行 Silo 的终端按 `Ctrl+C` 停止临时实例。
2. 删除实验数据与证书：

```bat
rmdir /s /q target\tls
```

`target/` 已被 `.gitignore` 忽略；不要把 `private.key` 或 truststore 提交到 Git。

## 8.7 故障排查

| 现象 | 原因 | 处理 |
|---|---|---|
| `PKIX path building failed` | JVM 不信任自签名证书 | 检查 `-Djavax.net.ssl.trustStore` 路径和密码 |
| `No subject alternative names matching IP address 127.0.0.1` | 证书缺 SAN | 用 `-addext "subjectAltName=IP:127.0.0.1"` 重新生成 |
| `SSLHandshakeException` | 证书/私钥不匹配或 certs-dir 错误 | 确认 `public.crt` 与 `private.key` 成对，路径正确 |
| 9443/9444 被占用 | 端口冲突 | 换端口，并同步改 endpoint |
| Silo 启动失败 | 证书路径/格式错误、数据目录权限 | 查看终端日志，确认使用绝对路径 |
| Demo 仍连 HTTP | 参数未生效 | 确认 `--learn.minio.endpoint=https://...` 在 jar 之后 |
| `UnsupportedClassVersionError` | 用了 JDK 8 | 使用上面的 JDK 21 绝对路径 |

## 8.8 生产替代方案

- 使用受信任 CA 签发的证书，配置正式 `--certs-dir`。
- 或在 Nginx/HAProxy 终止 TLS，后端到 Silo 走内网；确保预签名 URL 使用对外域名，Host/签名一致。
- 使用 KMS/密钥托管管理 SSE-C 密钥，或改用 SSE-KMS/SSE-S3。
- 永远不要在 HTTP 上传输 SSE-C 密钥。
