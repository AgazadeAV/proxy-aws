package ru.example.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class RelayClient {
    private static final OkHttpClient client = new OkHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String OPEN_URL = "https://zvgi0d7fm8.execute-api.us-east-2.amazonaws.com/prod/session/open";
    private static final String CLOSE_URL = "https://zvgi0d7fm8.execute-api.us-east-2.amazonaws.com/prod/session/%s";
    private static final String ENQUEUE_URL = "https://zvgi0d7fm8.execute-api.us-east-2.amazonaws.com/prod/session/task/enqueue";
    private static final String FETCH_URL = "https://zvgi0d7fm8.execute-api.us-east-2.amazonaws.com/prod/session/result/fetch";

    public void openSession(String sessionId, String token) throws Exception {
        Map<String, String> json = new HashMap<>();
        json.put("sessionId", sessionId);
        json.put("token", token);

        RequestBody body = RequestBody.create(mapper.writeValueAsString(json), MediaType.get("application/json"));
        Request request = new Request.Builder().url(OPEN_URL).post(body).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Failed to open session: " + response.code());
            }
        }
    }

    public void closeSession(String sessionId) throws Exception {
        String url = String.format(CLOSE_URL, sessionId);
        Request request = new Request.Builder().url(url).delete().build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Failed to close session: " + response.code());
            }
        }
    }

    public void sendPayload(String sessionId, String token, String target, byte[] data) throws Exception {
        Map<String, Object> json = new HashMap<>();
        json.put("sessionId", sessionId);
        json.put("token", token);
        json.put("target", target);
        json.put("payload", Base64.getEncoder().encodeToString(data));

        RequestBody body = RequestBody.create(mapper.writeValueAsString(json), MediaType.get("application/json"));
        Request request = new Request.Builder().url(ENQUEUE_URL).post(body).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Failed to send payload: " + response.code());
            }
        }
    }

    public byte[] fetchPayload(String sessionId) throws Exception {
        HttpUrl url = HttpUrl.parse(FETCH_URL).newBuilder()
                .addQueryParameter("sessionId", sessionId)
                .build();

        Request request = new Request.Builder().url(url).get().build();

        try (Response response = client.newCall(request).execute()) {
            if (response.code() != 200 || response.body() == null) {
                return new byte[0];
            }

            String body = response.body().string();
            Map<String, Object> map = mapper.readValue(body, Map.class);
            String payload = (String) map.get("payload");

            return payload != null ? Base64.getDecoder().decode(payload) : new byte[0];
        }
    }
}
