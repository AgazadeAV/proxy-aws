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

        if (token == null) {
            System.err.println("Usage: java -jar agent.jar -c <token>");
            System.exit(1);
        }

        String sessionId = TokenUtil.extractSessionId(token);

        System.out.println("[AgentApp] Starting agent for session: " + sessionId);

        AgentClient client = new AgentClient(BASE_URL);
        SessionSocketStore socketStore = new SessionSocketStore();
        TaskProcessor processor = new TaskProcessor(socketStore);

        while (true) {
            try {
                byte[] task = client.pollTask(sessionId);
                if (task == null) {
                    Thread.sleep(200);
                    continue;
                }

                byte[] result = processor.process(sessionId, task);
                client.submitResult(sessionId, result);

            } catch (Exception e) {
                System.err.println("[AgentApp] Error: " + e.getMessage());
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {}
            }
        }
    }
}
