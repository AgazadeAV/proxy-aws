package ru.example.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import ru.example.agent.dto.CommandMessage;

@RequiredArgsConstructor
public class AgentTaskPoller implements Runnable {

    private final String sessionId;
    private final AgentRelayClient client;
    private final AgentCommandProcessor processor;

    private volatile boolean running = true;

    public void stop() {
        running = false;
    }

    @Override
    @SuppressWarnings("BusyWait")
    public void run() {
        System.out.println("[AgentTaskPoller] Started polling for session: " + sessionId);
        ObjectMapper mapper = new ObjectMapper();

        while (running) {
            try {
                String json = client.pollTask(sessionId);
                if (json != null && !json.isBlank()) {
                    CommandMessage command = mapper.readValue(json, CommandMessage.class);
                    processor.process(command, sessionId);
                }
                Thread.sleep(500);
            } catch (Exception e) {
                System.err.println("[AgentTaskPoller] Error: " + e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
        }
        System.out.println("[AgentTaskPoller] Stopped.");
    }
}
