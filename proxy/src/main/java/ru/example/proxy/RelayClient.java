package ru.example.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

public class RelayClient {
    private final String baseUrl;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RelayClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void openSession(String sessionId, String token) throws IOException {
        HttpUrl url = HttpUrl.parse(baseUrl + "/session/open");
        Map<String, String> body = Map.of("sessionId", sessionId, "token", token);
        System.out.printf("[RelayClient] Opening session: %s%n", sessionId);
        post(url, body);
        System.out.printf("[RelayClient] Session opened: %s%n", sessionId);
    }

    public void closeSession(String sessionId) throws IOException {
        HttpUrl url = HttpUrl.parse(baseUrl + "/session/" + sessionId);
        System.out.printf("[RelayClient] Closing session: %s%n", sessionId);
        Request request = new Request.Builder().url(url).delete().build();
        httpClient.newCall(request).execute().close();
        System.out.printf("[RelayClient] Session closed: %s%n", sessionId);
    }

    public void sendPayload(String sessionId, byte[] payload) throws IOException {
        HttpUrl url = HttpUrl.parse(baseUrl + "/session/task/enqueue");
        String encoded = Base64.getEncoder().encodeToString(payload);
        Map<String, Object> body = Map.of("sessionId", sessionId, "payload", encoded);
        System.out.printf("[RelayClient] Sending payload to session %s (%d bytes)%n", sessionId, payload.length);
        post(url, body);
        System.out.printf("[RelayClient] Payload sent to session %s%n", sessionId);
    }

    public byte[] fetchPayload(String sessionId) throws IOException {
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(baseUrl + "/session/result/fetch"))
                .newBuilder().addQueryParameter("sessionId", sessionId).build();

        System.out.printf("[RelayClient] Fetching result for session %s...%n", sessionId);
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 204) {
                System.out.printf("[RelayClient] No result available for session %s (204)%n", sessionId);
                return null;
            }

            if (!response.isSuccessful()) {
                assert response.body() != null;
                throw new IOException("Failed to fetch result: " + response.code() + " - " + response.body().string());
            }

            String json = response.body().string();
            Map<?, ?> map = objectMapper.readValue(json, Map.class);
            byte[] decoded = Base64.getDecoder().decode((String) map.get("payload"));
            System.out.printf("[RelayClient] Fetched %d bytes for session %s%n", decoded.length, sessionId);
            return decoded;
        }
    }

    private void post(HttpUrl url, Map<?, ?> body) throws IOException {
        RequestBody requestBody = RequestBody.create(
                objectMapper.writeValueAsString(body),
                MediaType.get("application/json")
        );
        Request request = new Request.Builder().url(url).post(requestBody).build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                assert response.body() != null;
                String errorBody = response.body().string();
                System.err.printf("[RelayClient] POST failed: %d - %s%n", response.code(), errorBody);
                throw new IOException("POST failed: " + response.code() + " - " + errorBody);
            }
        }
    }
}
