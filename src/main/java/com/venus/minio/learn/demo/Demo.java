package com.venus.minio.learn.demo;

/**
 * 一个可独立运行的教学演示主题。
 */
public interface Demo {

    /** 命令行主题名，例如 {@code object}。 */
    String name();

    /** 一句话说明。 */
    String description();

    /** 执行演示；断言失败时抛出异常。 */
    void run(DemoSupport support) throws Exception;
}
