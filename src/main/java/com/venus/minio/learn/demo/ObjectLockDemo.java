package com.venus.minio.learn.demo;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.minio.DisableObjectLegalHoldArgs;
import io.minio.EnableObjectLegalHoldArgs;
import io.minio.GetBucketVersioningArgs;
import io.minio.GetObjectLockConfigurationArgs;
import io.minio.GetObjectRetentionArgs;
import io.minio.IsObjectLegalHoldEnabledArgs;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetObjectLockConfigurationArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ObjectLockConfiguration;
import io.minio.messages.Retention;
import io.minio.messages.RetentionDurationDays;
import io.minio.messages.RetentionMode;
import io.minio.messages.VersioningConfiguration;

@Component
public class ObjectLockDemo implements Demo {

    @Override
    public String name() {
        return "lock";
    }

    @Override
    public String description() {
        return "对象锁 GOVERNANCE 保留策略、legal hold 与 bypass 删除";
    }

    @Override
    public void run(DemoSupport s) throws Exception {
        s.header("ObjectLockDemo - WORM 对象锁");
        String bucket = "learn-lock-" + UUID.randomUUID().toString().substring(0, 8);
        String key = "audit/immutable.txt";
        s.createFreshObjectLockBucket(bucket);

        VersioningConfiguration versioning = s.client().getBucketVersioning(
                GetBucketVersioningArgs.builder().bucket(bucket).build());
        s.checkEquals(VersioningConfiguration.Status.ENABLED, versioning.status(),
                "对象锁桶自动启用版本控制");

        s.client().setObjectLockConfiguration(SetObjectLockConfigurationArgs.builder()
                .bucket(bucket)
                .config(new ObjectLockConfiguration(RetentionMode.GOVERNANCE, new RetentionDurationDays(1)))
                .build());
        ObjectLockConfiguration lockConfig = s.client().getObjectLockConfiguration(
                GetObjectLockConfigurationArgs.builder().bucket(bucket).build());
        s.checkEquals(RetentionMode.GOVERNANCE, lockConfig.mode(), "默认保留模式为 GOVERNANCE");
        s.note("默认保留时长: " + lockConfig.duration());

        byte[] body = "immutable audit record".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String versionId = s.client().putObject(PutObjectArgs.builder()
                .bucket(bucket).object(key)
                .stream(new java.io.ByteArrayInputStream(body), body.length, -1)
                .contentType("text/plain; charset=utf-8")
                .build()).versionId();
        s.check(versionId != null, "写入对象返回 versionId");

        Retention retention = s.client().getObjectRetention(GetObjectRetentionArgs.builder()
                .bucket(bucket).object(key).versionId(versionId).build());
        s.checkEquals(RetentionMode.GOVERNANCE, retention.mode(), "对象继承默认 GOVERNANCE 保留策略");
        s.check(retention.retainUntilDate() != null
                        && retention.retainUntilDate().isAfter(ZonedDateTime.now()),
                "保留截止时间在未来: " + retention.retainUntilDate());

        s.client().enableObjectLegalHold(EnableObjectLegalHoldArgs.builder()
                .bucket(bucket).object(key).versionId(versionId).build());
        s.check(s.client().isObjectLegalHoldEnabled(IsObjectLegalHoldEnabledArgs.builder()
                        .bucket(bucket).object(key).versionId(versionId).build()),
                "legal hold 已开启");
        s.client().disableObjectLegalHold(DisableObjectLegalHoldArgs.builder()
                .bucket(bucket).object(key).versionId(versionId).build());
        s.check(!s.client().isObjectLegalHoldEnabled(IsObjectLegalHoldEnabledArgs.builder()
                        .bucket(bucket).object(key).versionId(versionId).build()),
                "legal hold 已关闭");

        Exception denied = s.expectException("无 bypass 删除受保留策略保护的版本",
                ErrorResponseException.class,
                () -> s.client().removeObject(RemoveObjectArgs.builder()
                        .bucket(bucket).object(key).versionId(versionId).build()));
        s.note("服务端拒绝: " + s.errorCode(denied));

        s.client().removeObject(RemoveObjectArgs.builder()
                .bucket(bucket).object(key).versionId(versionId)
                .bypassGovernanceMode(true).build());
        s.check(s.listItems(bucket, key, true).isEmpty(), "bypassGovernanceMode 删除成功");

        s.cleanupBucket(bucket);
    }
}
