package com.venus.minio.learn.util;

import java.util.UUID;

public final class ObjectKeys {

    private ObjectKeys() {
    }

    /** 去掉开头的斜杠，避免生成空路径段；空值回退为自动生成的上传 key。 */
    public static String normalize(String key) {
        if (key == null || key.isBlank()) {
            return "uploads/" + UUID.randomUUID();
        }
        String normalized = key.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.isBlank() ? "uploads/" + UUID.randomUUID() : normalized;
    }

    public static String fileName(String key) {
        int slash = key.lastIndexOf('/');
        String name = slash >= 0 ? key.substring(slash + 1) : key;
        return name.isBlank() ? "download" : name;
    }
}
