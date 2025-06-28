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

            HttpUrl url = HttpUrl.parse(POLL_URL).newBuilder()
                    .addQueryParameter("sessionId", sessionId)
                    .build();

            Request request = new Request.Builder().url(url).get().build();
            try (Response response = client.newCall(request).execute()) {
                if (response.code() != 200 || response.body() == null) {
                    System.out.println("[PollTask] No task or error: " + response.code());
                    return;
                }

                String raw = response.body().string();
                if (raw.isBlank() || raw.equals("[]")) {
                    System.out.println("[PollTask] No task available.");
                    return;
                }

                Map<String, Object> task = mapper.readValue(raw, Map.class);
                task.put("token", token);
                task.put("sessionId", sessionId);

                String resultJson = new SessionHandler().handle(mapper.writeValueAsString(task));
                ResultSender.send(resultJson);
            }
        } catch (Exception e) {
            System.err.println("[PollTask] Error polling or handling: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
