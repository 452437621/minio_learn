package com.venus.minio.learn;

import java.util.Arrays;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.venus.minio.learn.config.MinioProperties;

/**
 * 同一个 jar 支持两种模式：
 * <ul>
 *   <li>无参数：启动 Spring Boot Web 文件服务（默认 8080）。</li>
 *   <li>{@code --demo=<topic>}：以非 Web 模式执行命令行演示。</li>
 * </ul>
 */
@SpringBootApplication
@EnableConfigurationProperties(MinioProperties.class)
public class Main {

    public static void main(String[] args) {
        boolean demoMode = Arrays.stream(args).anyMatch(arg -> arg.startsWith("--demo="));

        SpringApplication application = new SpringApplication(Main.class);
        if (demoMode) {
            application.setWebApplicationType(WebApplicationType.NONE);
            application.setBannerMode(Banner.Mode.OFF);
            // 演示模式下只保留教学输出，隐藏 Spring 启动日志
            System.setProperty("logging.level.root", "WARN");
            System.setProperty("logging.level.com.venus.minio.learn", "WARN");
        }
        application.run(args);
    }
}
