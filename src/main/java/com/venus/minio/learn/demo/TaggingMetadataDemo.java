package com.venus.minio.learn.demo;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.minio.DeleteObjectTagsArgs;
import io.minio.PutObjectArgs;
import io.minio.SetObjectTagsArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;

@Component
public class TaggingMetadataDemo implements Demo {

    @Override
    public String name() {
        return "tagging";
    }

    @Override
    public String description() {
        return "用户元数据与对象标签的设置、读取、覆盖、删除";
    }

    @Override
    public void run(DemoSupport s) throws Exception {
        s.header("TaggingMetadataDemo - 元数据与对象标签");
        String bucket = "learn-tagging";
        String key = "docs/tagged.txt";
        s.createFreshBucket(bucket);

        byte[] body = "metadata and tags".getBytes(StandardCharsets.UTF_8);
        s.client().putObject(PutObjectArgs.builder()
                .bucket(bucket).object(key)
                .stream(new ByteArrayInputStream(body), body.length, -1)
                .contentType("text/plain; charset=utf-8")
                .userMetadata(Map.of("author", "venus", "course", "minio-learn"))
                .tags(Map.of("env", "learn", "level", "basic"))
                .build());
        s.ok("写入对象并附带 userMetadata + tags");

        StatObjectResponse stat = s.client().statObject(StatObjectArgs.builder()
                .bucket(bucket).object(key).build());
        s.checkEquals("venus", stat.userMetadata().get("author"), "userMetadata.author 正确");
        s.checkEquals("minio-learn", stat.userMetadata().get("course"), "userMetadata.course 正确");

        Map<String, String> tags = s.tags(bucket, key);
        s.checkEquals("learn", tags.get("env"), "对象标签 env=learn");
        s.checkEquals("basic", tags.get("level"), "对象标签 level=basic");

        s.client().setObjectTags(SetObjectTagsArgs.builder()
                .bucket(bucket).object(key)
                .tags(Map.of("env", "prod", "owner", "team-a"))
                .build());
        Map<String, String> updated = s.tags(bucket, key);
        s.checkEquals("prod", updated.get("env"), "setObjectTags 覆盖 env");
        s.checkEquals("team-a", updated.get("owner"), "setObjectTags 新增 owner");
        s.check(!updated.containsKey("level"), "旧标签 level 已被替换");

        s.client().deleteObjectTags(DeleteObjectTagsArgs.builder()
                .bucket(bucket).object(key).build());
        s.check(s.tags(bucket, key).isEmpty(), "deleteObjectTags 后标签为空");
        s.cleanupBucket(bucket);
    }
}
