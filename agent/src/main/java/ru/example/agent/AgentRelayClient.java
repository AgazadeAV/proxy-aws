package ru.example.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import ru.example.agent.dto.EnvelopeDto;
import ru.example.agent.dto.SqsMessageDto;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class AgentRelayClient {

    private static final Region REGION = Region.US_EAST_2;
    private static final String BUCKET = "proxy-session-bucket";

    // ⚠️ как у тебя было — хардкод ключей. В проде так делать нельзя, но оставляю по совместимости.
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

    private String getQueueUrlByName(String queueName) {
        try {
            return sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).queueUrl();
        } catch (QueueDoesNotExistException e) {
            // Очередь ещё не создана (сессия не открыта на прокси) — это ок.
            return null;
        }
    }

    /** Читаем задачу без удаления из SQS. Возвращаем JSON команды и receiptHandle. */
    public PendingTask pollTask(String sessionId) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            String queueUrl = getQueueUrlByName(taskQueueName(sessionId));
            if (queueUrl == null) return null;

            ReceiveMessageResponse response = sqs.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .waitTimeSeconds(10)
                    .maxNumberOfMessages(1)
                    .build());

            if (response.messages().isEmpty()) return null;

            Message message = response.messages().get(0);
            String body = message.body();

            SqsMessageDto dto = mapper.readValue(body, SqsMessageDto.class);
            // Перестраховка — проверим, что сообщение нашей сессии.
            if (!sessionId.equals(dto.getSessionId())) {
                return null; // не удаляем, пусть вернётся по visibility timeout
            }

            String json = s3.getObjectAsBytes(GetObjectRequest.builder()
                            .bucket(BUCKET)
                            .key(dto.getS3Key())
                            .build())
                    .asUtf8String();

            System.out.printf("[pollTask] Received task: %s%n", dto.getS3Key());
            return new PendingTask(message.receiptHandle(), json);

        } catch (Exception e) {
            System.err.println("[pollTask] Error: " + e.getMessage());
            return null;
        }
    }

    /** Подтверждаем успешную обработку сообщения: удаляем из SQS. */
    public void ackTask(String sessionId, String receiptHandle) {
        try {
            String queueUrl = getQueueUrlByName(taskQueueName(sessionId));
            if (queueUrl == null) return;

            sqs.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(receiptHandle)
                    .build());
        } catch (Exception e) {
            System.err.println("[ackTask] Error: " + e.getMessage());
        }
    }

    /** Публикуем результат в per-session очередь результатов. */
    public void submitResult(String sessionId, String base64Payload) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            String key = String.format("sessions/%s/result_%s.json", sessionId, UUID.randomUUID());
            String json = mapper.writeValueAsString(new EnvelopeDto(base64Payload));

            s3.putObject(PutObjectRequest.builder()
                            .bucket(BUCKET)
                            .key(key)
                            .build(),
                    RequestBody.fromString(json));

            SqsMessageDto dto = SqsMessageDto.builder()
                    .sessionId(sessionId)
                    .s3Key(key)
                    .timestamp(Instant.now().toString())
                    .build();

            String body = mapper.writeValueAsString(dto);

            String resultQueueUrl = getQueueUrlByName(resultQueueName(sessionId));
            if (resultQueueUrl == null) {
                System.err.println("[submitResult] Result queue not found for session " + sessionId);
                return;
            }

            sqs.sendMessage(SendMessageRequest.builder()
                    .queueUrl(resultQueueUrl)
                    .messageGroupId(sessionId)
                    .messageDeduplicationId(UUID.randomUUID().toString())
                    .messageBody(body)
                    .build());

            System.out.printf("[submitResult] Sent result to SQS (%s)%n", key);
        } catch (Exception e) {
            System.err.println("[submitResult] Error: " + e.getMessage());
        }
    }
}
