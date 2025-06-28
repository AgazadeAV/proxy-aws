package ru.example.agent;

import java.util.Timer;

public class AgentApp {
    public static void main(String[] args) throws Exception {
        String token = null;

        // Получаем токен из аргументов: -c <token>
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

        System.out.println("[AgentApp] Agent started with token: " + token);

        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new PollTask(token), 0, 1000);

        while (true) {
            Thread.sleep(10_000);
        }
    }
}
