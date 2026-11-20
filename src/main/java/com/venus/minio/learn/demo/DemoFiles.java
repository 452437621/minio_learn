package com.venus.minio.learn.demo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 在 target/demo-tmp 下创建演示文件，避免把大文件放进堆内存。
 */
public final class DemoFiles {

    private DemoFiles() {
    }

    public static Path createSizedFile(String name, long size) throws IOException {
        Path directory = Path.of("target", "demo-tmp");
        Files.createDirectories(directory);
        Path file = directory.resolve(name);
        if (Files.exists(file)) {
            Files.delete(file);
        }
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            raf.setLength(size);
        }
        return file;
    }

    public static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // 演示环境的临时文件，删除失败不影响结果
        }
    }
}
