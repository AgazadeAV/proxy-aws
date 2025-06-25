package ru.example.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class AgentApp {
    private static final String AGENT_ID = "agent-001";
    private static final String POLL_URL = "https://sbbd0vxjdj.execute-api.us-east-2.amazonaws.com/prod/poll?agentId=" + AGENT_ID;
    private static final String RELAY_URL = "https://sbbd0vxjdj.execute-api.us-east-2.amazonaws.com/prod/relay-result";

    private static final OkHttpClient client = new OkHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TaskPoller(), 0, 1000);

        System.out.println("[AGENT] Started polling loop. Press Ctrl+C to stop.");
        while (true) {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException ignored) {}
        }
    }

    static class TaskPoller extends TimerTask {
        @Override
        public void run() {
            try {
                Request pollRequest = new Request.Builder().url(POLL_URL).get().build();
                try (Response response = client.newCall(pollRequest).execute()) {
                    if (response.code() != 200 || response.body() == null) return;

                    String raw = response.body().string();
                    System.out.println("[AGENT] Raw task: " + raw);

                    if (raw.isBlank() || raw.equals("[]")) return;

                    Map<String, Object> task = mapper.readValue(raw, Map.class);
                    String sessionId = ((String) task.get("PK")).split("#")[1];
                    String target = (String) task.get("target");
                    String payloadBase64 = (String) task.get("payload");

                    System.out.printf("[AGENT] Handling session %s → %s%n", sessionId, target);

                    String result = handleSession(target, payloadBase64);
                    sendResult(sessionId, result);
                }
            } catch (Exception e) {
                System.err.println("[AGENT] Poll error: " + e.getMessage());
            }
        }

        private String handleSession(String target, String payloadBase64) {
            try {
                String[] parts = target.split(":");
                String host = parts[0];
                int port = Integer.parseInt(parts[1]);
                byte[] payload = Base64.getDecoder().decode(payloadBase64);

                try (Socket socket = new Socket(host, port)) {
                    OutputStream out = socket.getOutputStream();
                    InputStream in = socket.getInputStream();

                    out.write(payload);
                    out.flush();

                    byte[] buffer = new byte[4096];
                    int read = in.read(buffer);
                    if (read == -1) return "";

                    byte[] trimmed = Arrays.copyOfRange(buffer, 0, read);
                    return Base64.getEncoder().encodeToString(trimmed);
                }
            } catch (Exception e) {
                System.err.println("[AGENT] Handle error: " + e.getMessage());
                return Base64.getEncoder().encodeToString(("[AGENT ERROR] " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
            }
        }

        private void sendResult(String sessionId, String base64) {
            try {
                String json = mapper.writeValueAsString(Map.of(
                        "agentId", AGENT_ID,
                        "sessionId", sessionId,
                        "payload", base64
                ));

                Request post = new Request.Builder()
                        .url(RELAY_URL)
                        .post(RequestBody.create(json, MediaType.get("application/json")))
                        .build();

                try (Response response = client.newCall(post).execute()) {
                    System.out.println("[AGENT] Relay status: " + response.code());
                }
            } catch (Exception e) {
                System.err.println("[AGENT] Relay error: " + e.getMessage());
            }
        }
    }
}
