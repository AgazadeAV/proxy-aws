package ru.example.proxy;

import okhttp3.*;

import java.io.IOException;

public class RelayClient {

    private final String baseUrl;
    private final OkHttpClient client = new OkHttpClient();
    private final MediaType JSON = MediaType.parse("application/json");

    public RelayClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void enqueueTask(String sessionId, String payloadJson) {
        String url = baseUrl + "/session/task/enqueue";
        String body = String.format("{\"sessionId\":\"%s\",\"payload\":%s}", sessionId, payloadJson);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body, JSON))
                .build();

        client.newCall(request).enqueue(new Callback() {
            public void onResponse(Call call, Response response) {
                System.out.println("[RelayClient] Enqueued task, status=" + response.code());
                response.close();
            }

            public void onFailure(Call call, IOException e) {
                System.err.println("[RelayClient] Failed to enqueue task: " + e.getMessage());
            }
        });
    }

    public String fetchResult(String sessionId) {
        String url = baseUrl + "/session/result/fetch?sessionId=" + sessionId;
        Request request = new Request.Builder().url(url).get().build();

        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 200 && response.body() != null) {
                return response.body().string();
            }
        } catch (IOException e) {
            System.err.println("[RelayClient] Fetch error: " + e.getMessage());
        }

        return null;
    }
}
