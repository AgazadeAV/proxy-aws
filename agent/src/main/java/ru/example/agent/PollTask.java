package ru.example.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.util.Map;
import java.util.TimerTask;

public class PollTask extends TimerTask {
    private static final OkHttpClient client = new OkHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String POLL_URL = "https://zvgi0d7fm8.execute-api.us-east-2.amazonaws.com/prod/session/task/poll";

    private final String token;

    public PollTask(String token) {
        this.token = token;
    }

    @Override
    public void run() {
        try {
            String sessionId = SessionTokenUtil.parseSessionId(token);
            System.out.printf("[PollTask] 🔄 Polling for session: %s%n", sessionId);

            HttpUrl url = HttpUrl.parse(POLL_URL).newBuilder()
                    .addQueryParameter("sessionId", sessionId)
                    .build();

            Request request = new Request.Builder().url(url).get().build();

            try (Response response = client.newCall(request).execute()) {
                int code = response.code();

                if (code != 200 || response.body() == null) {
                    System.out.printf("[PollTask] ⚠️ Poll failed: HTTP %d%n", code);
                    return;
                }

                String raw = response.body().string().trim();

                if (raw.isBlank() || raw.equals("[]")) {
                    System.out.printf("[PollTask] ⏳ No task available for session: %s%n", sessionId);
                    return;
                }

                System.out.printf("[PollTask] ✅ Task received: %s%n", raw);

                Map<String, Object> task = mapper.readValue(raw, Map.class);
                task.put("token", token);
                task.put("sessionId", sessionId);

                String input = mapper.writeValueAsString(task);
                String resultJson = new SessionHandler().handle(input);

                System.out.printf("[PollTask] 📤 Sending result: %s%n", resultJson);
                ResultSender.send(resultJson);
            }
        } catch (Exception e) {
            System.err.printf("[PollTask] ❌ Error: %s%n", e.getMessage());
            e.printStackTrace();
        }
    }
}
