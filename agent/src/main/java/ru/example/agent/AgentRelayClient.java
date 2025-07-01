package ru.example.agent;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@RequiredArgsConstructor
public class AgentRelayClient {

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

    public String pollTask(String sessionId) {
        try {
            ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                    .queueUrl(taskQueueUrl)
                    .waitTimeSeconds(10)
                    .maxNumberOfMessages(1)
                    .build();

            ReceiveMessageResponse response = sqs.receiveMessage(request);
            if (response.messages().isEmpty()) return null;

            Message message = response.messages().get(0);
            String body = message.body();

            // Delete message from queue
            sqs.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(taskQueueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build());

            System.out.printf("[pollTask] Received task: %s%n", body);
            return body;
        } catch (Exception e) {
            System.err.println("[pollTask] Error: " + e.getMessage());
            return null;
        }
    }

    public void submitResult(String sessionId, String base64Payload) {
        try {
            String key = String.format("sessions/%s/result_%s.bin", sessionId, UUID.randomUUID());
            byte[] data = Base64.getDecoder().decode(base64Payload);

            // Upload to S3
            s3.putObject(PutObjectRequest.builder()
                            .bucket(BUCKET)
                            .key(key)
                            .build(),
                    RequestBody.fromBytes(data));

            // Send message to SQS
            String body = String.format("{\"sessionId\": \"%s\", \"s3Key\": \"%s\", \"timestamp\": \"%s\"}",
                    sessionId, key, Instant.now());

            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(resultQueueUrl)
                    .messageGroupId(sessionId)
                    .messageDeduplicationId(UUID.randomUUID().toString())
                    .messageBody(body)
                    .build();

            sqs.sendMessage(request);
            System.out.printf("[submitResult] Sent result to SQS (%s)%n", key);
        } catch (Exception e) {
            System.err.println("[submitResult] Error: " + e.getMessage());
        }
    }
}
