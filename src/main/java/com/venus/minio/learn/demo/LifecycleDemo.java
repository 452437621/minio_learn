package com.venus.minio.learn.demo;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import io.minio.DeleteBucketLifecycleArgs;
import io.minio.GetBucketLifecycleArgs;
import io.minio.SetBucketLifecycleArgs;
import io.minio.SetBucketVersioningArgs;
import io.minio.messages.AbortIncompleteMultipartUpload;
import io.minio.messages.Expiration;
import io.minio.messages.LifecycleConfiguration;
import io.minio.messages.LifecycleRule;
import io.minio.messages.NoncurrentVersionExpiration;
import io.minio.messages.RuleFilter;
import io.minio.messages.Status;
import io.minio.messages.VersioningConfiguration;

@Component
public class LifecycleDemo implements Demo {

    @Override
    public String name() {
        return "lifecycle";
    }

    @Override
    public String description() {
        return "生命周期规则：过期、清理未完成分片、非当前版本过期";
    }

    @Override
    public void run(DemoSupport s) throws Exception {
        s.header("LifecycleDemo - 生命周期规则（异步扫描，不等待实际删除）");
        String bucket = "learn-lifecycle";
        s.createFreshBucket(bucket);
        s.client().setBucketVersioning(SetBucketVersioningArgs.builder().bucket(bucket)
                .config(new VersioningConfiguration(VersioningConfiguration.Status.ENABLED, null))
                .build());

        // 先验证 Silo 的兼容性差异：仅含 AbortIncompleteMultipartUpload 的规则会被拒绝。
        LifecycleRule abortOnly = new LifecycleRule(
                Status.ENABLED,
                new AbortIncompleteMultipartUpload(1),
                null,
                new RuleFilter("uploads/"),
                "abort-only",
                null, null, null);
        try {
            s.client().setBucketLifecycle(SetBucketLifecycleArgs.builder().bucket(bucket)
                    .config(new LifecycleConfiguration(List.of(abortOnly)))
                    .build());
            s.note("当前 Silo 接受 abort-only 规则（不同版本行为可能不同）");
        } catch (io.minio.errors.ErrorResponseException e) {
            s.expected("Silo 拒绝 abort-only 规则 -> " + s.errorCode(e));
        }

        LifecycleRule expire = new LifecycleRule(
                Status.ENABLED,
                null,
                new Expiration((ZonedDateTime) null, 1, null),
                new RuleFilter("tmp/"),
                "expire-tmp-after-1-day",
                null, null, null);

        // Silo 当前版本会拒绝“只有 AbortIncompleteMultipartUpload”的规则
        // （InvalidArgument）。把 abort 动作与 Expiration 放在同一条规则中即可通过校验，
        // 这也是生产上常见的组合：清理未完成分片 + 到期删除对象。
        LifecycleRule abortMpu = new LifecycleRule(
                Status.ENABLED,
                new AbortIncompleteMultipartUpload(1),
                new Expiration((ZonedDateTime) null, 30, null),
                new RuleFilter("uploads/"),
                "abort-incomplete-multipart-and-expire",
                null, null, null);

        LifecycleRule noncurrent = new LifecycleRule(
                Status.ENABLED,
                null, null,
                new RuleFilter("docs/"),
                "expire-noncurrent-1-day",
                new NoncurrentVersionExpiration(1),
                null, null);

        s.client().setBucketLifecycle(SetBucketLifecycleArgs.builder().bucket(bucket)
                .config(new LifecycleConfiguration(List.of(expire, abortMpu, noncurrent)))
                .build());
        s.ok("setBucketLifecycle 写入 3 条规则");

        LifecycleConfiguration config = s.client().getBucketLifecycle(
                GetBucketLifecycleArgs.builder().bucket(bucket).build());
        s.checkEquals(3, config.rules().size(), "getBucketLifecycle 返回 3 条规则");
        for (LifecycleRule rule : config.rules()) {
            s.note(rule.id() + " -> prefix=" + rule.filter().prefix()
                    + ", expiration=" + (rule.expiration() != null)
                    + ", abortMpu=" + (rule.abortIncompleteMultipartUpload() != null)
                    + ", noncurrent=" + (rule.noncurrentVersionExpiration() != null));
        }
        boolean abortPreserved = config.rules().stream()
                .anyMatch(rule -> rule.abortIncompleteMultipartUpload() != null);
        if (abortPreserved) {
            s.note("服务端保留了 AbortIncompleteMultipartUpload 动作");
        } else {
            s.note("Silo 当前 get 结果中未保留 AbortIncompleteMultipartUpload 动作（只保留了 Expiration）");
        }
        s.note("规则配置成功不代表对象立即删除：Silo 会由后台 scanner 按日扫描");

        s.client().deleteBucketLifecycle(DeleteBucketLifecycleArgs.builder().bucket(bucket).build());
        LifecycleConfiguration afterDelete = s.client().getBucketLifecycle(
                GetBucketLifecycleArgs.builder().bucket(bucket).build());
        s.check(afterDelete == null,
                "删除后 getBucketLifecycle 返回 null（SDK 8.6.0 对 NoSuchLifecycleConfiguration 的处理）");
        s.cleanupBucket(bucket);
    }
}
