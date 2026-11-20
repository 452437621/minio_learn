package com.venus.minio.learn.demo;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

import io.minio.PutObjectArgs;

@Component
public class MultipartDemo implements Demo {

    private static final long SIZE = 20L * 1024 * 1024;
    private static final long PART_SIZE = 5L * 1024 * 1024;

    @Override
    public String name() {
        return "multipart";
    }

    @Override
    public String description() {
        return "20MiB 大文件分片上传与 ETag 分片后缀";
    }

    @Override
    public void run(DemoSupport s) throws Exception {
        s.header("MultipartDemo - 分片上传");
        String bucket = "learn-multipart";
        String key = "big/20m.bin";
        s.createFreshBucket(bucket);

        Path file = DemoFiles.createSizedFile("20m.bin", SIZE);
        try {
            String etag;
            try (InputStream in = Files.newInputStream(file)) {
                etag = s.client().putObject(PutObjectArgs.builder()
                        .bucket(bucket).object(key)
                        .stream(in, SIZE, PART_SIZE)
                        .contentType("application/octet-stream")
                        .build()).etag();
            }
            s.note("对象大小 20 MiB，partSize 5 MiB，共 4 片");
            s.note("ETag = " + etag);
            String normalizedEtag = etag.replace("\"", "");
            s.check(normalizedEtag.contains("-"), "ETag 带分片后缀（形如 md5-4）");
            s.check(normalizedEtag.endsWith("-4"), "ETag 以 -4 结尾（4 个分片）");
            s.checkEquals(SIZE, s.stat(bucket, key).size(), "上传后对象大小等于 20 MiB");

            byte[] tail = s.readRange(bucket, key, SIZE - 1024, 1024);
            s.checkEquals(1024, tail.length, "Range GET 读取最后 1 KiB 成功");
        } finally {
            DemoFiles.deleteQuietly(file);
        }

        s.note("观察方式: mcli stat silo/" + bucket + "/" + key + " 或控制台对象详情");
        s.cleanupBucket(bucket);
    }
}
