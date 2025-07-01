package ru.example.agent;

import lombok.RequiredArgsConstructor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;

@RequiredArgsConstructor
public class AgentRelayClient {
    private final OkHttpClient client = new OkHttpClient();
    private final String baseUrl;

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
        String bodyJson = String.format("{\"sessionId\": \"%s\", \"payload\": \"%s\"}", sessionId, payload);
        RequestBody body = RequestBody.Companion.create(bodyJson, MediaType.get("application/json"));

        Request request = new Request.Builder()
                .url(baseUrl + "/session/result/submit")
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            assert response.body() != null;
            System.out.println("[submitResult] Response: " + response.code() + " " + response.body().string());
        }
    }
}
