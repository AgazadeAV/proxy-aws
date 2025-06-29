package ru.example.agent;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;

public class AgentRelayClient {
    private final OkHttpClient client = new OkHttpClient();
    private final String baseUrl;

    public AgentRelayClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String pollTask(String sessionId) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/session/task/poll?sessionId=" + sessionId)
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 200 && response.body() != null) {
                return response.body().string();
            }
        }
        return null;
    }

    public void submitResult(String sessionId, String payload) throws IOException {
        RequestBody body = RequestBody.create(
                MediaType.get("application/json"),
                String.format("{\"sessionId\": \"%s\", \"payload\": \"%s\"}", sessionId, payload)
        );
        Request request = new Request.Builder()
                .url(baseUrl + "/session/result/submit")
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            System.out.println("[submitResult] Response: " + response.code() + " " + response.body().string());
        }
    }
}
