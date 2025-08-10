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
                PendingTask pending = client.pollTask(sessionId);
                if (pending != null && pending.getJson() != null && !pending.getJson().isBlank()) {
                    try {
                        CommandMessage command = mapper.readValue(pending.getJson(), CommandMessage.class);
                        processor.process(command, sessionId);
                        // Успех — подтверждаем удалением
                        client.ackTask(sessionId, pending.getReceiptHandle());
                    } catch (Exception ex) {
                        // Не ack — задача вернётся после visibility timeout
                        System.err.println("[AgentTaskPoller] Processing error: " + ex.getMessage());
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
}
