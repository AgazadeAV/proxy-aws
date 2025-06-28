package ru.example.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class ProxySessionHandler {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final OkHttpClient client = new OkHttpClient();

    private static final String ENQUEUE_URL = "https://zvgi0d7fm8.execute-api.us-east-2.amazonaws.com/prod/session/task/enqueue";
    private static final String RESULT_URL = "https://zvgi0d7fm8.execute-api.us-east-2.amazonaws.com/prod/session/result/fetch";
    private static final String OPEN_URL = "https://zvgi0d7fm8.execute-api.us-east-2.amazonaws.com/prod/session/open";
    private static final String CLOSE_URL = "https://zvgi0d7fm8.execute-api.us-east-2.amazonaws.com/prod/session/%s";

    public static void openSession(String sessionId, String token) throws Exception {
        Map<String, Object> json = new HashMap<>();
        json.put("sessionId", sessionId);
        json.put("token", token);

        RequestBody body = RequestBody.create(
                mapper.writeValueAsString(json),
                MediaType.get("application/json")
        );

        Request req = new Request.Builder()
                .url(OPEN_URL)
                .post(body)
                .build();

        try (Response response = client.newCall(req).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Failed to open session: " + response.code());
            }
        }
    }

    public static void send(String sessionId, String token, byte[] data) throws Exception {
        Map<String, Object> json = new HashMap<>();
        json.put("sessionId", sessionId);
        json.put("token", token);
        json.put("payload", Base64.getEncoder().encodeToString(data));

        RequestBody body = RequestBody.create(
                mapper.writeValueAsString(json),
                MediaType.get("application/json")
        );

        Request request = new Request.Builder().url(ENQUEUE_URL).post(body).build();
        try (Response response = client.newCall(request).execute()) {
            if (response.code() >= 300) {
                System.err.println("[Proxy] Error response: " + response.code());
            }
        }
    }

    public static byte[] receive(String sessionId, String token) throws Exception {
        HttpUrl url = HttpUrl.parse(RESULT_URL).newBuilder()
                .addQueryParameter("sessionId", sessionId)
                .build();

        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (response.code() != 200 || response.body() == null) return new byte[0];

            String body = response.body().string();
            Map<String, Object> map = mapper.readValue(body, Map.class);
            String payload = (String) map.get("payload");
            return Base64.getDecoder().decode(payload);
        }
    }

    public static void close(String sessionId, String token) {
        String url = String.format(CLOSE_URL, sessionId);

        Request request = new Request.Builder()
                .url(url)
                .delete()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.code() != 200) {
                System.err.println("[Proxy] Failed to close session: " + response.code());
            } else {
                System.out.println("[Proxy] Session closed: " + sessionId);
            }
        } catch (Exception e) {
            System.err.println("[Proxy] Close session error: " + e.getMessage());
        }
    }
}
