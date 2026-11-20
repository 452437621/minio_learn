package com.venus.minio.learn.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

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
import io.minio.messages.RetentionDurationDays;
import io.minio.messages.RetentionMode;
import io.minio.messages.VersioningConfiguration;

@EnabledIfEnvironmentVariable(named = "MINIO_IT", matches = "true")
class ObjectLockIT extends MinioItBase {

    @Test
    void governanceRetentionAndLegalHold() throws Exception {
        String bucket = newObjectLockBucket();
        assertThat(client.getBucketVersioning(GetBucketVersioningArgs.builder().bucket(bucket).build())
                .status()).isEqualTo(VersioningConfiguration.Status.ENABLED);

        client.setObjectLockConfiguration(SetObjectLockConfigurationArgs.builder().bucket(bucket)
                .config(new ObjectLockConfiguration(RetentionMode.GOVERNANCE, new RetentionDurationDays(1)))
                .build());
        assertThat(client.getObjectLockConfiguration(GetObjectLockConfigurationArgs.builder()
                .bucket(bucket).build()).mode()).isEqualTo(RetentionMode.GOVERNANCE);

        String key = "audit/immutable.txt";
        byte[] body = "immutable".getBytes(StandardCharsets.UTF_8);
        String versionId = client.putObject(PutObjectArgs.builder()
                .bucket(bucket).object(key)
                .stream(new ByteArrayInputStream(body), body.length, -1)
                .contentType("text/plain; charset=utf-8")
                .build()).versionId();
        assertThat(versionId).isNotBlank();

        var retention = client.getObjectRetention(GetObjectRetentionArgs.builder()
                .bucket(bucket).object(key).versionId(versionId).build());
        assertThat(retention.mode()).isEqualTo(RetentionMode.GOVERNANCE);
        assertThat(retention.retainUntilDate()).isNotNull();

        client.enableObjectLegalHold(EnableObjectLegalHoldArgs.builder()
                .bucket(bucket).object(key).versionId(versionId).build());
        assertThat(client.isObjectLegalHoldEnabled(IsObjectLegalHoldEnabledArgs.builder()
                .bucket(bucket).object(key).versionId(versionId).build())).isTrue();
        client.disableObjectLegalHold(DisableObjectLegalHoldArgs.builder()
                .bucket(bucket).object(key).versionId(versionId).build());
        assertThat(client.isObjectLegalHoldEnabled(IsObjectLegalHoldEnabledArgs.builder()
                .bucket(bucket).object(key).versionId(versionId).build())).isFalse();

        assertThatThrownBy(() -> client.removeObject(RemoveObjectArgs.builder()
                .bucket(bucket).object(key).versionId(versionId).build()))
                .isInstanceOf(ErrorResponseException.class);

        client.removeObject(RemoveObjectArgs.builder()
                .bucket(bucket).object(key).versionId(versionId)
                .bypassGovernanceMode(true).build());
    }
}
