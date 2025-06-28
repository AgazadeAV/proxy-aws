package ru.example.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ru.example.shared.SessionTokenUtil;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class AgentApp {
    public static String AGENT_TOKEN;
    public static final String AGENT_ID = "agent-001";
    public static final String POLL_URL = "https://zvgi0d7fm8.execute-api.us-east-2.amazonaws.com/prod/session/task/poll";
    public static final String RELAY_URL = "https://zvgi0d7fm8.execute-api.us-east-2.amazonaws.com/prod/session/result/submit";

    private static final OkHttpClient client = new OkHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < args.length - 1; i++) {
            if ("-c".equals(args[i])) {
                AGENT_TOKEN = args[i + 1];
                break;
            }
        }

        if (AGENT_TOKEN == null) {
            System.err.println("Usage: java -jar agent.jar -c <token>");
            System.exit(1);
        }

        System.out.println("[AGENT] Started with token: " + AGENT_TOKEN);

        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new PollTask(), 0, 1000);

        while (true) Thread.sleep(10_000);
    }

    static class PollTask extends TimerTask {
        @Override
        public void run() {
            try {
                String sessionId = SessionTokenUtil.parseSessionId(AGENT_TOKEN);

                HttpUrl url = HttpUrl.parse(POLL_URL).newBuilder()
                        .addQueryParameter("sessionId", sessionId)
                        .build();

                System.out.println("[AGENT] Polling from: " + url);

                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.code() != 200 || response.body() == null) {
                        System.out.println("[AGENT] No response or error: " + response.code());
                        return;
                    }

                    String raw = response.body().string();
                    System.out.println("[AGENT] Raw response: " + raw);

                    if (raw.isBlank() || raw.equals("[]")) {
                        System.out.println("[AGENT] Empty task response");
                        return;
                    }

                    Map<String, Object> task = mapper.readValue(raw, Map.class);
                    String result = new SessionHandler().handle(mapper.writeValueAsString(task));
                    sendResult(result);
                }
            } catch (Exception e) {
                System.err.println("[AGENT] Poll error: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private void sendResult(String resultJson) {
            try {
                System.out.println("[AGENT] Sending result: " + resultJson);
                RequestBody body = RequestBody.create(resultJson, MediaType.get("application/json"));
                Request post = new Request.Builder().url(RELAY_URL).post(body).build();

                try (Response resp = client.newCall(post).execute()) {
                    System.out.println("[AGENT] Sent result. Status: " + resp.code());
                }
            } catch (Exception e) {
                System.err.println("[AGENT] Failed to send result: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
