package ru.example.proxy;

import lombok.RequiredArgsConstructor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;

@RequiredArgsConstructor
public class ProxyRelayClient {
    private final OkHttpClient client = new OkHttpClient();
    private final String baseUrl;

    public void openSession(String sessionId, String token) throws IOException {
        String bodyJson = String.format("{\"sessionId\": \"%s\", \"token\": \"%s\"}", sessionId, token);
        RequestBody body = RequestBody.Companion.create(bodyJson, MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(baseUrl + "/session/open")
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            assert response.body() != null;
            System.out.println("[openSession] Response: " + response.code() + " " + response.body().string());
        }
    }

    public void deleteSession(String sessionId) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/session/" + sessionId)
                .delete()
                .build();
        try (Response response = client.newCall(request).execute()) {
            assert response.body() != null;
            System.out.println("[deleteSession] Response: " + response.code() + " " + response.body().string());
        }
    }

    public void enqueueTask(String sessionId, String payload) throws IOException {
        String escapedPayload = payload.replace("\"", "\\\"");
        String bodyJson = String.format("{\"sessionId\": \"%s\", \"payload\": \"%s\"}", sessionId, escapedPayload);
        RequestBody body = RequestBody.Companion.create(bodyJson, MediaType.get("application/json"));

        Request request = new Request.Builder()
                .url(baseUrl + "/session/task/enqueue")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            assert response.body() != null;
            System.out.println("[enqueueTask] Response: " + response.code() + " " + response.body().string());
        }
    }

    public String fetchResult(String sessionId) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/session/result/fetch?sessionId=" + sessionId)
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 200 && response.body() != null) {
                return response.body().string();
            }
        }
        return null;
    }
}
