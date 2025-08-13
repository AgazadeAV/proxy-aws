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
import software.amazon.awssdk.services.sqs.model.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class ProxyRelayClient {

    private static final Region REGION = Region.US_EAST_2;
    private static final String BUCKET = "proxy-session-bucket";

    // ⚠️ оставляю как было для совместимости. В проде – IAM role/env.
    private static final AwsBasicCredentials CREDS = AwsBasicCredentials.create(
            "AKIAUMUKCDOJS7SHLF4H",
            "VGuzhS342yNoy9bdVwkCWMGlXS7KmQhs7Iv/D170"
    );
    private static final StaticCredentialsProvider PROVIDER = StaticCredentialsProvider.create(CREDS);

    private final S3Client s3 = S3Client.builder()
            .region(REGION)
            .credentialsProvider(PROVIDER)
            .build();

    private final SqsClient sqs = SqsClient.builder()
            .region(REGION)
            .credentialsProvider(PROVIDER)
            .build();

    private String taskQueueName(String sessionId) { return "proxy-to-agent-" + sessionId + ".fifo"; }
    private String resultQueueName(String sessionId) { return "agent-to-proxy-" + sessionId + ".fifo"; }

    private String getQueueUrlByName(String name) {
        try {
            return sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(name).build()).queueUrl();
        } catch (QueueDoesNotExistException e) {
            return null;
        }
    }

    private void createFifoQueueIfMissing(String name) {
        try {
            // попытка получить URL – если нет, создадим
            String url = getQueueUrlByName(name);
            if (url != null) return;

            sqs.createQueue(CreateQueueRequest.builder()
                    .queueName(name)
                    .attributes(Map.of(
                            QueueAttributeName.FIFO_QUEUE, "true",
                            QueueAttributeName.CONTENT_BASED_DEDUPLICATION, "false",
                            QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS, "10"
                    ))
                    .build());
        } catch (QueueNameExistsException ignored) {
        } catch (Exception e) {
            System.err.println("[createFifoQueueIfMissing] " + e.getMessage());
        }
    }

    private void deleteQueueIfExists(String name) {
        try {
            String url = getQueueUrlByName(name);
            if (url == null) return;
            sqs.deleteQueue(DeleteQueueRequest.builder().queueUrl(url).build());
        } catch (QueueDoesNotExistException ignored) {
        } catch (Exception e) {
            System.err.println("[deleteQueueIfExists] " + e.getMessage());
        }
    }

    public void openSession(String sessionId, String token) {
        createFifoQueueIfMissing(taskQueueName(sessionId));
        createFifoQueueIfMissing(resultQueueName(sessionId));
        System.out.printf("[openSession] Session opened: %s (%s)%n", sessionId, token);
    }

    public void deleteSession(String sessionId) {
        deleteQueueIfExists(taskQueueName(sessionId));
        deleteQueueIfExists(resultQueueName(sessionId));
        System.out.printf("[deleteSession] Session deleted: %s%n", sessionId);
    }

    /** Отправка команды агенту: кладём команду в S3 и пушим указатель в per-session очередь задач. */
    public void enqueueTask(String sessionId, String commandJson) throws Exception {
        String key = String.format("sessions/%s/task_%s.json", sessionId, UUID.randomUUID());

        s3.putObject(PutObjectRequest.builder()
                        .bucket(BUCKET)
                        .key(key)
                        .build(),
                RequestBody.fromString(commandJson)
        );

        SqsMessageDto dto = SqsMessageDto.builder()
                .sessionId(sessionId)
                .s3Key(key)
                .timestamp(Instant.now().toString())
                .build();
        ObjectMapper mapper = new ObjectMapper();
        String body = mapper.writeValueAsString(dto);

        String queueUrl = getQueueUrlByName(taskQueueName(sessionId));
        if (queueUrl == null) {
            System.err.println("[enqueueTask] Task queue not found for session " + sessionId);
            return;
        }

        sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageGroupId(sessionId)
                .messageDeduplicationId(UUID.randomUUID().toString())
                .messageBody(body)
                .build());

        System.out.printf("[enqueueTask] Sent task to SQS (%s)%n", key);
    }

    /** Чтение результата от агента из per-session очереди результатов. */
    public String fetchResult(String sessionId) {
        String queueUrl = getQueueUrlByName(resultQueueName(sessionId));
        if (queueUrl == null) return null;

        ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(10)
                .build();

        List<Message> messages = sqs.receiveMessage(request).messages();
        if (messages.isEmpty()) return null;

        Message msg = messages.get(0);
        String body = msg.body();

        String s3Key = extractS3Key(body);
        if (s3Key == null) return null;

        String content = s3.getObjectAsBytes(GetObjectRequest.builder()
                        .bucket(BUCKET)
                        .key(s3Key)
                        .build())
                .asUtf8String();

        // удаляем сообщение из очереди
        sqs.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(msg.receiptHandle())
                .build());

        // S3-объект удаляем сразу (на следующем шаге можем сделать подтверждение после записи в канал)
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
            ru.example.proxy.dto.SqsMessageDto dto = mapper.readValue(json, ru.example.proxy.dto.SqsMessageDto.class);
            return dto.getS3Key();
        } catch (Exception e) {
            System.err.println("[extractS3Key] Error: " + e.getMessage());
            return null;
        }
    }
}
