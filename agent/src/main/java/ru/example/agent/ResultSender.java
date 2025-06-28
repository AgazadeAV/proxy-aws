package ru.example.agent;

import okhttp3.*;

import java.io.IOException;

public class ResultSender {
    private static final OkHttpClient client = new OkHttpClient();
    private static final String RELAY_URL = "https://zvgi0d7fm8.execute-api.us-east-2.amazonaws.com/prod/session/result/submit";

    public static void send(String json) {
        System.out.println("[ResultSender] 📤 Preparing to send result...");

        RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(RELAY_URL)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            int code = response.code();
            String message = response.message();
            String responseBody = response.body() != null ? response.body().string() : "null";

            if (code >= 200 && code < 300) {
                System.out.printf("[ResultSender] ✅ Result sent successfully. HTTP %d %s%nResponse: %s%n", code, message, responseBody);
            } else {
                System.out.printf("[ResultSender] ⚠️ Unexpected response. HTTP %d %s%nBody: %s%n", code, message, responseBody);
            }
        } catch (IOException e) {
            System.err.printf("[ResultSender] ❌ Failed to send result: %s%n", e.getMessage());
            e.printStackTrace();
        }
    }
}
