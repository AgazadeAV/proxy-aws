package ru.example.agent;

import okhttp3.*;

import java.io.IOException;

public class ResultSender {
    private static final OkHttpClient client = new OkHttpClient();
    private static final String RELAY_URL = "https://zvgi0d7fm8.execute-api.us-east-2.amazonaws.com/prod/session/result/submit";

    public static void send(String json) {
        RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
        Request request = new Request.Builder().url(RELAY_URL).post(body).build();

        try (Response response = client.newCall(request).execute()) {
            assert response.body() != null;
            System.out.println("[ResultSender] Sent result. Status: " + response.code() + " " + response.message() + " " + response.body().string());
        } catch (IOException e) {
            System.err.println("[ResultSender] Error sending result: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
