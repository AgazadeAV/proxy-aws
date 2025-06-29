package ru.example.proxy;

import okhttp3.*;

import java.io.IOException;

public class ProxyRelayClient {
    private final OkHttpClient client = new OkHttpClient();
    private final String baseUrl;

    public ProxyRelayClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void openSession(String sessionId, String token) throws IOException {
        RequestBody body = RequestBody.create(
                MediaType.get("application/json"),
                String.format("{\"sessionId\": \"%s\", \"token\": \"%s\"}", sessionId, token)
        );
        Request request = new Request.Builder()
                .url(baseUrl + "/session/open")
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            System.out.println("[openSession] Response: " + response.code() + " " + response.body().string());
        }
    }

    public void deleteSession(String sessionId) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/session/" + sessionId)
                .delete()
                .build();
        try (Response response = client.newCall(request).execute()) {
            System.out.println("[deleteSession] Response: " + response.code() + " " + response.body().string());
        }
    }

    public void enqueueTask(String sessionId, String payload) throws IOException {
        RequestBody body = RequestBody.create(
                MediaType.get("application/json"),
                String.format("{\"sessionId\": \"%s\", \"payload\": \"%s\"}", sessionId, payload)
        );
        Request request = new Request.Builder()
                .url(baseUrl + "/session/task/enqueue")
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            System.out.println("[enqueueTask] Response: " + response.code() + " " + response.body().string());
        }
    }

    public void fetchResult(String sessionId) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/session/result/fetch?sessionId=" + sessionId)
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            System.out.println("[fetchResult] Response: " + response.code() + " " + response.body().string());
        }
    }
}
