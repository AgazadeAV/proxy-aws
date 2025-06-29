package ru.example.agent;

public class AgentApp {

    private static final String BASE_URL = "https://zvgi0d7fm8.execute-api.us-east-2.amazonaws.com/prod";

    @SuppressWarnings({"InfiniteLoopStatement", "BusyWait"})
    public static void main(String[] args) {
        String token = null;

        for (int i = 0; i < args.length - 1; i++) {
            if ("-c".equals(args[i])) {
                token = args[i + 1];
                break;
            }
        }

        if (token == null || token.isBlank()) {
            System.err.println("[AgentApp] ❌ Usage: java -jar agent.jar -c <token>");
            System.exit(1);
        }

        String sessionId = TokenUtil.extractSessionId(token);
        System.out.printf("[AgentApp] ✅ Starting agent for session: %s (token: %s)%n", sessionId, token);

        AgentClient client = new AgentClient(BASE_URL);
        SessionSocketStore socketStore = new SessionSocketStore();
        TaskProcessor processor = new TaskProcessor(socketStore);

        while (true) {
            try {
                byte[] task = client.pollTask(sessionId);
                if (task == null) {
                    System.out.printf("[AgentApp] ⏳ No task for session %s. Sleeping...%n", sessionId);
                    Thread.sleep(200);
                    continue;
                }

                System.out.printf("[AgentApp] 📥 Received task (%d bytes) for session %s%n", task.length, sessionId);
                byte[] result = processor.process(sessionId, task);
                System.out.printf("[AgentApp] ✅ Processed task, result = %d bytes%n", result.length);

                client.submitResult(sessionId, result);
                System.out.printf("[AgentApp] 📤 Submitted result for session %s%n", sessionId);

            } catch (Exception e) {
                System.err.printf("[AgentApp] ❌ Error in session %s: %s%n", sessionId, e.getMessage());
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {}
            }
        }
    }
}
