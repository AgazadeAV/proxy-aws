package ru.example.agent;

import java.util.Base64;

public class AgentApp {

    public static void main(String[] args) {
        String token = parseToken(args);
        if (token == null) {
            System.err.println("Usage: java -jar agent.jar -c <token>");
            return;
        }

        String sessionId = extractSessionId(token);
        if (sessionId == null) {
            System.err.println("Invalid token format");
            return;
        }

        AgentRelayClient relayClient = new AgentRelayClient(
                "https://sqs.us-east-2.amazonaws.com/302010997651/proxy-to-agent.fifo",
                "https://sqs.us-east-2.amazonaws.com/302010997651/agent-to-proxy.fifo"
        );
        AgentSessionManager sessionManager = new AgentSessionManager();
        AgentCommandProcessor commandProcessor = new AgentCommandProcessor(sessionManager, relayClient);
        AgentTaskPoller poller = new AgentTaskPoller(sessionId, relayClient, commandProcessor);

        Thread thread = new Thread(poller);
        thread.start();

        System.out.println("[AgentApp] Agent started for session: " + sessionId);
    }

    private static String parseToken(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("-c".equals(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }

    private static String extractSessionId(String token) {
        try {
            byte[] decoded = Base64.getDecoder().decode(token);
            return new String(decoded);
        } catch (Exception e) {
            return null;
        }
    }
}
