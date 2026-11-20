package com.venus.minio.learn.demo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.venus.minio.learn.config.MinioProperties;

import io.minio.MinioClient;

/**
 * 读取 {@code --demo=<topic>}，执行单个主题或全部主题。
 * 没有 --demo 参数时（Web 模式）直接返回。
 */
@Component
public class DemoRunner implements ApplicationRunner {

    private static final List<String> ORDER = List.of(
            "bucket", "object", "presign", "versioning", "lock",
            "lifecycle", "multipart", "policy", "ssec", "tagging");

    private final MinioClient client;
    private final MinioProperties properties;
    private final Map<String, Demo> demos = new LinkedHashMap<>();

    @Value("${demo:}")
    private String topic;

    @Value("${keep:false}")
    private boolean keep;

    public DemoRunner(MinioClient client, MinioProperties properties, List<Demo> demoList) {
        this.client = client;
        this.properties = properties;
        for (Demo demo : demoList) {
            demos.put(demo.name(), demo);
        }
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (topic == null || topic.isBlank()) {
            return;
        }
        if ("list".equals(topic)) {
            printTopics();
            return;
        }
        List<Demo> selected = "all".equals(topic)
                ? ORDER.stream().map(this::require).toList()
                : List.of(require(topic));

        System.out.println();
        System.out.println("Silo / MinIO 学习演示");
        System.out.println("S3 endpoint : " + properties.endpoint());
        System.out.println("Console     : " + properties.consoleUrl());
        System.out.println("Keep buckets: " + keep);

        for (Demo demo : selected) {
            DemoSupport support = new DemoSupport(client, properties, keep);
            long start = System.currentTimeMillis();
            try {
                demo.run(support);
            } catch (Exception e) {
                System.out.println();
                System.out.println("== " + demo.name() + " FAILED: " + e.getMessage());
                throw e;
            }
            long cost = System.currentTimeMillis() - start;
            System.out.printf("%n== %s done: %d checks, %d expected errors (%d ms) ==%n",
                    demo.name(), support.checks(), support.expectedErrors(), cost);
        }
    }

    private Demo require(String name) {
        Demo demo = demos.get(name);
        if (demo == null) {
            throw new IllegalArgumentException("未知演示主题: " + name + "，可用主题: " + String.join(", ", ORDER));
        }
        return demo;
    }

    private void printTopics() {
        System.out.println("可用演示主题：");
        for (String name : ORDER) {
            Demo demo = demos.get(name);
            System.out.printf("  %-10s %s%n", name, demo == null ? "(未实现)" : demo.description());
        }
        System.out.println("  all        依次运行全部主题");
        System.out.println();
        System.out.println("用法: scripts\\demo.bat <topic> [--keep=true]");
    }
}
