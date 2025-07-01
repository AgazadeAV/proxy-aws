package ru.example.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import ru.example.proxy.dto.SqsMessageDto;
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

import java.time.Instant;
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

    public void enqueueTask(String sessionId, String commandJson) throws Exception {
        String key = String.format("sessions/%s/task_%s.json", sessionId, UUID.randomUUID());

        // Upload to S3
        s3.putObject(PutObjectRequest.builder()
                        .bucket(BUCKET)
                        .key(key)
                        .build(),
                RequestBody.fromString(commandJson)
        );

        // Send message to SQS with S3 key
        SqsMessageDto dto = SqsMessageDto.builder()
                .sessionId(sessionId)
                .s3Key(key)
                .timestamp(Instant.now().toString())
                .build();
        ObjectMapper mapper = new ObjectMapper();
        String body = mapper.writeValueAsString(dto);

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
            ObjectMapper mapper = new ObjectMapper();
            SqsMessageDto dto = mapper.readValue(json, SqsMessageDto.class);
            return dto.getS3Key();
        } catch (Exception e) {
            System.err.println("[extractS3Key] Error: " + e.getMessage());
            return null;
        }
    }
}
