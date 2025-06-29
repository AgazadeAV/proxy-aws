package ru.example.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

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
        post(url, body);
    }

    public void closeSession(String sessionId) throws IOException {
        HttpUrl url = HttpUrl.parse(baseUrl + "/session/" + sessionId);
        assert url != null;
        Request request = new Request.Builder().url(url).delete().build();
        httpClient.newCall(request).execute().close();
    }

    public void sendPayload(String sessionId, byte[] payload) throws IOException {
        HttpUrl url = HttpUrl.parse(baseUrl + "/session/task/enqueue");
        Map<String, Object> body = Map.of(
                "sessionId", sessionId,
                "payload", Base64.getEncoder().encodeToString(payload)
        );
        post(url, body);
    }

    public byte[] fetchPayload(String sessionId) throws IOException {
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(baseUrl + "/session/result/fetch"))
                .newBuilder().addQueryParameter("sessionId", sessionId).build();
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 204) return null;
            assert response.body() != null;
            String json = response.body().string();
            Map<?, ?> map = objectMapper.readValue(json, Map.class);
            return Base64.getDecoder().decode((String) map.get("payload"));
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
                throw new IOException("Failed: " + response.code() + " - " + response.body().string());
            }
        }
    }
}
