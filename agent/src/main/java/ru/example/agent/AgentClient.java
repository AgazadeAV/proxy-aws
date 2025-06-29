package ru.example.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

public class AgentClient {
    private static final MediaType JSON = MediaType.get("application/json");
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String pollUrl;
    private final String submitUrl;

    public AgentClient(String baseUrl) {
        this.pollUrl = baseUrl + "/session/task/poll";
        this.submitUrl = baseUrl + "/session/result/submit";
    }

    public byte[] pollTask(String sessionId) throws IOException {
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(pollUrl))
                .newBuilder()
                .addQueryParameter("sessionId", sessionId)
                .build();

        System.out.printf("[AgentClient] 📡 Polling task from %s%n", url);

        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 204) {
                System.out.printf("[AgentClient] ⛔ No task available (204)%n");
                return null;
            }
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                System.err.printf("[AgentClient] ❌ Failed to poll task (%d): %s%n", response.code(), body);
                throw new IOException("Failed to poll task: " + response);
            }

            assert response.body() != null;
            String body = response.body().string();
            Map<String, Object> task = mapper.readValue(body, new TypeReference<>() {});
            String base64 = (String) task.get("payload");
            byte[] decoded = Base64.getDecoder().decode(base64);

            System.out.printf("[AgentClient] ✅ Task received: %d bytes%n", decoded.length);
            return decoded;
        }
    }

    public void submitResult(String sessionId, byte[] payload) throws IOException {
        String encoded = Base64.getEncoder().encodeToString(payload);
        String json = mapper.writeValueAsString(Map.of(
                "sessionId", sessionId,
                "payload", encoded
        ));

        System.out.printf("[AgentClient] 📤 Submitting result (%d bytes) to %s%n", payload.length, submitUrl);

        Request request = new Request.Builder()
                .url(submitUrl)
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                System.err.printf("[AgentClient] ❌ Failed to submit result (%d): %s%n", response.code(), body);
                throw new IOException("Failed to submit result: " + response);
            }

            System.out.printf("[AgentClient] ✅ Result submitted for session %s%n", sessionId);
        }
    }
}
