package ru.example.proxy;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class ProxyRelayClient {

    private final String taskQueueUrl;
    private final String resultQueueUrl;

    private static final Region REGION = Region.US_EAST_2;
    private static final String BUCKET = "proxy-session-bucket";
    private static final AwsBasicCredentials CREDS = AwsBasicCredentials.create("AKIAUMUKCDOJS7SHLF4H", "VGuzhS342yNoy9bdVwkCWMGlXS7KmQhs7Iv/D170");
    private static final StaticCredentialsProvider PROVIDER = StaticCredentialsProvider.create(CREDS);

    private final S3Client s3 = S3Client.builder()
            .region(REGION)
            .credentialsProvider(PROVIDER)
            .build();

    private final SqsClient sqs = SqsClient.builder()
            .region(REGION)
            .credentialsProvider(PROVIDER)
            .build();

    public void openSession(String sessionId, String token) {
        System.out.printf("[openSession] Session opened: %s (%s)%n", sessionId, token);
    }

    public void deleteSession(String sessionId) {
        System.out.printf("[deleteSession] Session deleted: %s%n", sessionId);
    }

    public void enqueueTask(String sessionId, String commandJson) {
        String key = String.format("sessions/%s/task_%s.json", sessionId, UUID.randomUUID());

        // Upload to S3
        s3.putObject(PutObjectRequest.builder()
                        .bucket(BUCKET)
                        .key(key)
                        .build(),
                RequestBody.fromString(commandJson)
        );

        // Send message to SQS with S3 key
        String body = String.format("{\"sessionId\": \"%s\", \"s3Key\": \"%s\"}", sessionId, key);

        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(taskQueueUrl)
                .messageGroupId(sessionId)
                .messageDeduplicationId(UUID.randomUUID().toString())
                .messageBody(body)
                .build();

        sqs.sendMessage(request);
        System.out.printf("[enqueueTask] Sent task to SQS (%s)%n", key);
    }

    public String fetchResult(String sessionId) {
        ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(resultQueueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(10)
                .build();

        List<Message> messages = sqs.receiveMessage(request).messages();

        if (messages.isEmpty()) {
            return null;
        }

        Message msg = messages.get(0);
        String body = msg.body();

        String s3Key = extractS3Key(body);
        if (s3Key == null) {
            return null;
        }

        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(BUCKET)
                .key(s3Key)
                .build();

        String content = s3.getObjectAsBytes(getRequest).asUtf8String();

        // Delete message from queue
        sqs.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(resultQueueUrl)
                .receiptHandle(msg.receiptHandle())
                .build());

        // Optional: delete from S3
        s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(BUCKET)
                .key(s3Key)
                .build());

        System.out.printf("[fetchResult] Received result from %s%n", s3Key);
        return content;
    }

    private String extractS3Key(String json) {
        try {
            int index = json.indexOf("\"s3Key\":");
            if (index == -1) return null;
            int start = json.indexOf("\"", index + 8);
            int end = json.indexOf("\"", start + 1);
            return json.substring(start + 1, end);
        } catch (Exception e) {
            return null;
        }
    }
}
