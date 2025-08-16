package org.example.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.webrtc.CredsProvider;
import org.example.webrtc.WebRtcTransport;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;

public class AgentApp {

    // ======== ЗАПОЛНИ ПОД СЕБЯ ========
    private static final String AWS_ACCESS_KEY = "AKIAUMUKCDOJ2IEBJYIS";
    private static final String AWS_SECRET_KEY = "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";
    private static final Region AWS_REGION = Region.US_EAST_2; // пример
    private static final String S3_BUCKET = "tunnel-signal";
    private static final String S3_PREFIX = ""; // можно "" или "some/prefix/"
    // ===================================

    public static void main(String[] args) throws Exception {
        Path cfgDir = Path.of(System.getProperty("configs.dir", "configs"));

        ObjectMapper om = new ObjectMapper();
        AgentConfig cfg = om.readValue(cfgDir.resolve("app.agent.json").toFile(), AgentConfig.class);

        // --- WebRTC transport ---
        CredsProvider creds = CredsProvider.fromDir(cfgDir);
        WebRtcTransport transport = new WebRtcTransport(creds);

        StreamRouter router = new StreamRouter(cfg.getSessionId(), transport, new TcpDialer(cfg.getConnectTimeoutMs()));
        transport.setListener(router);
        transport.setSessionId(cfg.getSessionId());
        transport.start();

        // --- S3 сигналлинг ---
        try (S3Client s3 = buildS3()) {
            String offerKey = key("sessions/" + cfg.getSessionId() + "/offer.sdp");
            String answerKey = key("sessions/" + cfg.getSessionId() + "/answer.sdp");

            // ждём offer
            String sdpOffer = waitAndGet(s3, S3_BUCKET, offerKey, Duration.ofMinutes(3), Duration.ofSeconds(2));
            System.out.println("[Agent] Offer received from S3");
            transport.setRemoteOffer(sdpOffer);

            // создаём answer и кладём в S3
            boolean trickle = false;
            String sdpAnswer = transport.createAnswer(trickle);
            putText(s3, S3_BUCKET, answerKey, sdpAnswer);
            System.out.println("[Agent] Answer uploaded to S3");
        }

        System.out.println("[Agent] started. Press Ctrl+C to exit");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                transport.stop();
            } catch (Exception ignored) {
            }
            router.shutdown();
        }));

        Thread.currentThread().join();
    }

    // ===== helpers =====

    private static S3Client buildS3() {
        return S3Client.builder()
                .region(AWS_REGION)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(AWS_ACCESS_KEY, AWS_SECRET_KEY)
                ))
                .build();
    }

    private static String key(String k) {
        if (S3_PREFIX == null || S3_PREFIX.isBlank()) return k;
        return (S3_PREFIX.endsWith("/") ? S3_PREFIX : S3_PREFIX + "/") + k;
    }

    private static void putText(S3Client s3, String bucket, String key, String content) {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .cacheControl("no-cache")
                        .contentType("text/plain; charset=utf-8")
                        .build(),
                RequestBody.fromBytes(content.getBytes(StandardCharsets.UTF_8)));
    }

    private static boolean exists(S3Client s3, String bucket, String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) return false;
            throw e;
        }
    }

    private static String getText(S3Client s3, String bucket, String key) {
        return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .asString(StandardCharsets.UTF_8);
    }

    private static String waitAndGet(S3Client s3, String bucket, String key,
                                     Duration timeout, Duration interval) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (exists(s3, bucket, key)) {
                return getText(s3, bucket, key);
            }
            Thread.sleep(interval.toMillis());
        }
        throw new RuntimeException("Timeout waiting for s3://" + bucket + "/" + key);
    }

    private static String argValue(String[] args, String key) {
        return Arrays.stream(args)
                .filter(a -> a.startsWith(key + "="))
                .map(a -> a.substring((key + "=").length()))
                .findFirst().orElse(null);
    }
}
