package ru.example.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Base64;

public class AgentTaskPoller implements Runnable {

    private final String sessionId;
    private final AgentRelayClient client;
    private final AgentCommandProcessor processor;
    private volatile boolean running = true;

    public AgentTaskPoller(String sessionId, AgentRelayClient client, AgentCommandProcessor processor) {
        this.sessionId = sessionId;
        this.client = client;
        this.processor = processor;
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        System.out.println("[AgentTaskPoller] Started polling for session: " + sessionId);
        while (running) {
            try {
                String json = client.pollTask(sessionId);
                if (json != null && !json.isBlank()) {
                    Task task = Task.fromJson(json);
                    if (task != null) {
                        processor.process(task, sessionId);
                    }
                }
                Thread.sleep(500);
            } catch (Exception e) {
                System.err.println("[AgentTaskPoller] Error: " + e.getMessage());
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        }
        System.out.println("[AgentTaskPoller] Stopped.");
    }

    public static class Task {
        public String command;
        public String address;
        public int port;
        public String payload;

        public static Task fromJson(String json) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(json); // Внешний JSON
                String inner = root.get("payload").asText(); // Вложенный JSON как строка

                JsonNode payloadNode = mapper.readTree(inner); // Вложенный JSON

                Task task = new Task();
                task.command = payloadNode.has("command") ? payloadNode.get("command").asText() : null;
                task.address = payloadNode.has("address") ? payloadNode.get("address").asText() : null;
                task.port = payloadNode.has("port") ? payloadNode.get("port").asInt() : 0;
                task.payload = payloadNode.has("payload") ? payloadNode.get("payload").asText() : null;

                return task;
            } catch (Exception e) {
                System.err.println("[Task] Failed to parse: " + e.getMessage());
                return null;
            }
        }

        public byte[] getPayloadBytes() {
            if (payload == null || payload.isBlank()) return new byte[0];
            return Base64.getDecoder().decode(payload);
        }
    }
}
