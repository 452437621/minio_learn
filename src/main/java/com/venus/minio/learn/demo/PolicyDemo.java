package com.venus.minio.learn.demo;

import java.net.http.HttpResponse;

import org.springframework.stereotype.Component;

import io.minio.DeleteBucketPolicyArgs;
import io.minio.SetBucketPolicyArgs;

@Component
public class PolicyDemo implements Demo {

    private static final String PUBLIC_READ_POLICY = """
            {
              "Version": "2012-10-17",
              "Statement": [{
                "Effect": "Allow",
                "Principal": {"AWS": ["*"]},
                "Action": ["s3:GetObject"],
                "Resource": ["arn:aws:s3:::%s/*"]
              }]
            }
            """;

    @Override
    public String name() {
        return "policy";
    }

    @Override
    public String description() {
        return "匿名只读桶策略：设置后 200、删除策略后 403";
    }

    @Override
    public void run(DemoSupport s) throws Exception {
        s.header("PolicyDemo - 桶策略与匿名访问");
        String bucket = "learn-policy";
        String key = "public/hello.txt";
        s.createFreshBucket(bucket);
        s.putText(bucket, key, "this object is public", "text/plain; charset=utf-8");

        String url = s.objectUrl(bucket, key);
        s.checkStatus("未设置策略时匿名 GET 被拒绝", 403, s.httpStatus(url));

        s.client().setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket)
                .config(PUBLIC_READ_POLICY.formatted(bucket))
                .build());
        s.ok("setBucketPolicy 写入匿名只读策略");

        HttpResponse<String> anonymous = s.httpGet(url);
        s.checkStatus("设置策略后匿名 GET 成功", 200, anonymous.statusCode());
        s.checkEquals("this object is public", anonymous.body(), "匿名 GET 内容正确");

        s.client().deleteBucketPolicy(DeleteBucketPolicyArgs.builder().bucket(bucket).build());
        s.checkStatus("删除策略后匿名 GET 再次被拒绝", 403, s.httpStatus(url));
        s.cleanupBucket(bucket);
    }
}
