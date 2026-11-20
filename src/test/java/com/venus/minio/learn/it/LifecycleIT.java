package com.venus.minio.learn.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

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

@EnabledIfEnvironmentVariable(named = "MINIO_IT", matches = "true")
class LifecycleIT extends MinioItBase {

    @Test
    void lifecycleConfigurationRoundTrip() throws Exception {
        String bucket = newBucket();
        client.setBucketVersioning(SetBucketVersioningArgs.builder().bucket(bucket)
                .config(new VersioningConfiguration(VersioningConfiguration.Status.ENABLED, null))
                .build());

        LifecycleRule expire = new LifecycleRule(Status.ENABLED, null,
                new Expiration((ZonedDateTime) null, 1, null),
                new RuleFilter("tmp/"), "expire-tmp", null, null, null);
        // Silo 当前拒绝 abort-only 规则；与 Expiration 组合可以正常工作。
        LifecycleRule abort = new LifecycleRule(Status.ENABLED,
                new AbortIncompleteMultipartUpload(1),
                new Expiration((ZonedDateTime) null, 30, null),
                new RuleFilter("uploads/"), "abort-mpu-and-expire", null, null, null);
        LifecycleRule noncurrent = new LifecycleRule(Status.ENABLED, null, null,
                new RuleFilter("docs/"), "expire-noncurrent",
                new NoncurrentVersionExpiration(1), null, null);

        client.setBucketLifecycle(SetBucketLifecycleArgs.builder().bucket(bucket)
                .config(new LifecycleConfiguration(List.of(expire, abort, noncurrent)))
                .build());

        LifecycleConfiguration config = client.getBucketLifecycle(
                GetBucketLifecycleArgs.builder().bucket(bucket).build());
        assertThat(config.rules()).hasSize(3);
        assertThat(config.rules()).extracting(rule -> rule.filter().prefix())
                .containsExactlyInAnyOrder("tmp/", "uploads/", "docs/");

        client.deleteBucketLifecycle(DeleteBucketLifecycleArgs.builder().bucket(bucket).build());
        // SDK 8.6.0 对 NoSuchLifecycleConfiguration 返回 null，而不是抛异常
        assertThat(client.getBucketLifecycle(
                GetBucketLifecycleArgs.builder().bucket(bucket).build())).isNull();
    }
}
