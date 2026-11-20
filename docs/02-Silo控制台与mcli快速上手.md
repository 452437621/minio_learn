# 02 · Silo 控制台与 mcli 快速上手

## 2.1 打开控制台

浏览器访问：

```text
http://127.0.0.1:9001
```

登录：

```text
用户名：minioadmin
密码：  minioadmin
```

控制台可以完成：

- 创建/删除桶
- 上传、下载、预览对象
- 查看对象元数据、标签、版本
- 配置桶的匿名访问策略（Access Rules）
- 查看版本控制、生命周期、对象锁
- 查看集群/节点/磁盘指标
- 创建 IAM 用户和策略（Silo 保留完整控制台）

> 控制台在 9001，S3 API 在 9000。控制台里展示的 endpoint 可能包含多个网卡地址，SDK 用 `127.0.0.1:9000` 即可。

## 2.2 mc 与 mcli

- **mc**：MinIO 官方命令行客户端。
- **mcli**：PGSTY 为 Silo 维护的客户端，命令与 mc 基本一致。
- Silo 文档推荐使用 `pgsty/mc`，与上游 MinIO/MC 的兼容性为“尽力而为”。

本机当前没有安装 mc/mcli。安装步骤（Windows）：

1. 打开 Silo 文档或 GitHub Releases：
   - `https://silo.pgsty.com/download/`
   - `https://github.com/pgsty/mc/releases`
2. 下载 Windows `amd64` 版本（压缩包或单个 exe，以发布页为准）。
3. 解压得到 `mc.exe` 或 `mcli.exe`。
4. 放到已在 `PATH` 中的目录，例如：
   ```text
   C:\Users\<你的用户名>\bin\
   ```
   或把所在目录加入系统环境变量 `Path`。
5. 新开一个终端验证：
   ```bat
   mcli --version
   mc --version
   ```

> 发布页的资产名称可能变化，不要把某个固定文件名写死；以页面说明为准。官方 mc 也可以尝试 `https://dl.min.io/client/mc/release/windows-amd64/mc.exe`，但上游社区分发已不稳定。

## 2.3 配置别名

```bat
mcli alias set silo http://127.0.0.1:9000 minioadmin minioadmin
mcli alias list
```

之后所有命令用 `silo/<bucket>/<key>` 引用对象。

```bat
mcli admin info silo
```

第一次连接会显示版本、节点、磁盘、容量等信息。

## 2.4 常用命令速查

### 桶

```bat
mcli mb silo/demo-bucket
mcli ls silo
mcli rb silo/demo-bucket
mcli rb --force silo/demo-bucket
```

### 对象

```bat
mcli cp .\README.md silo/demo-bucket/docs/README.md
mcli ls -r silo/demo-bucket
mcli stat silo/demo-bucket/docs/README.md
mcli cat silo/demo-bucket/docs/README.md
mcli cp silo/demo-bucket/docs/README.md .\downloaded.md
mcli rm silo/demo-bucket/docs/README.md
mcli rm --recursive --force silo/demo-bucket
```

### 镜像/同步

```bat
mcli mirror .\local-dir silo/demo-bucket/backup/
mcli mirror --overwrite --remove silo/demo-bucket/backup/ .\restore-dir
```

`mirror` 适合同步目录，但要注意删除语义和版本；生产备份建议结合版本控制与独立存储。

### 匿名访问

```bat
mcli anonymous get silo/demo-bucket
mcli anonymous set download silo/demo-bucket
mcli anonymous set none silo/demo-bucket
```

`set download` 会让桶内对象可匿名下载，仅用于临时实验；演示结束务必 `set none`。

### 版本控制

```bat
mcli version enable silo/demo-bucket
mcli version info silo/demo-bucket
mcli version suspend silo/demo-bucket
mcli ls --versions --recursive silo/demo-bucket
mcli rm --versions --recursive --force silo/demo-bucket
```

### 对象锁 / 保留

```bat
mcli retention set --default GOVERNANCE 1d silo/locked-bucket
mcli retention get silo/locked-bucket/docs/a.txt
mcli legalhold set silo/locked-bucket/docs/a.txt
mcli legalhold clear silo/locked-bucket/docs/a.txt
```

### 生命周期

新版客户端通常是 `ilm rule` 子命令；旧版可能是 `ilm add/ls/rm`。以 `mcli ilm --help` 为准。

```bat
mcli ilm rule add --expire-days 1 --prefix "tmp/" silo/demo-bucket
mcli ilm rule ls silo/demo-bucket
mcli ilm rule rm --id "..." silo/demo-bucket
```

### 管理

```bat
mcli admin info silo
mcli admin user add silo app-user app-secret
mcli admin user list silo
mcli admin policy attach silo readwrite --user app-user
mcli admin trace silo
```

### 事件通知

```bat
mcli event add silo/demo-bucket arn:minio:sqs::PRIMARY:webhook
```

事件通知需要先配置目标（webhook/AMQP/Kafka 等），单机学习环境默认没有目标；本项目只做文档说明，不提供可运行 Demo。

## 2.5 控制台 vs mcli vs SDK

| 方式 | 适合 | 特点 |
|---|---|---|
| 控制台 9001 | 观察、手工操作、演示 | 直观，但不利于自动化 |
| mcli/mc | 运维、批量、排错 | 命令丰富，适合脚本 |
| Java SDK | 应用集成 | 类型安全、可测试、可嵌入业务 |

## 2.6 安全习惯

- 不要把 `minioadmin/minioadmin` 用于任何共享或生产环境。
- 匿名策略用完立即删除。
- `mcli admin trace` 会显示请求头，注意脱敏。
- 删除版本化桶前先确认 `--versions`，避免误删历史数据。
- 生产访问使用 TLS；SSE-C 强制要求 HTTPS。
