package org.example.common.signaling;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class S3SignalingProvider implements SignalingProvider {
    private final S3Client s3;
    private final String bucket;
    private final String prefix; // можно "" или "some/subdir/"

    public S3SignalingProvider(S3Client s3, String bucket, String prefix) {
        this.s3 = s3;
        this.bucket = bucket;
        this.prefix = (prefix == null || prefix.isBlank()) ? "" :
                (prefix.endsWith("/") ? prefix : prefix + "/");
    }

    private String key(String key) {
        return prefix + key;
    }

    @Override
    public void putText(String key, String content) throws Exception {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key(key))
                        .cacheControl("no-cache")
                        .contentType("text/plain; charset=utf-8")
                        .build(),
                RequestBody.fromBytes(content.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public String getText(String key) throws Exception {
        return s3.getObjectAsBytes(GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(key(key))
                        .build())
                .asString(StandardCharsets.UTF_8);
    }

    @Override
    public boolean exists(String key) throws Exception {
        try {
            s3.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key(key))
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) return false;
            throw e;
        }
    }

    @Override
    public void delete(String key) throws Exception {
        s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key(key))
                .build());
    }

    /**
     * Удобный поллер: ждём появления ключа с таймаутом
     */
    public String waitAndGet(String key, Duration timeout, Duration interval) throws Exception {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (exists(key)) {
                return getText(key);
            }
            Thread.sleep(interval.toMillis());
        }
        throw new RuntimeException("Timeout waiting for " + key(key));
    }
}
