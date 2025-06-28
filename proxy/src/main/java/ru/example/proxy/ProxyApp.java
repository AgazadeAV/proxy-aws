package ru.example.proxy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyApp {
    private static final int PORT = 1080;

    public static void main(String[] args) {
        System.out.println("[ProxyApp] Starting SOCKS5 proxy on port " + PORT);

        ExecutorService executor = Executors.newFixedThreadPool(50);
        RelayClient relayClient = new RelayClient();
        SessionManager sessionManager = new SessionManager(relayClient);

        // 👉 Поток для ввода команд с клавиатуры
        Thread commandThread = new Thread(() -> handleCommands(sessionManager, relayClient));
        commandThread.setDaemon(true);
        commandThread.start();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                executor.submit(() -> {
                    new Socks5Handler(clientSocket, sessionManager, relayClient).run();
                });
            }
        } catch (IOException e) {
            System.err.println("[ProxyApp] Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleCommands(SessionManager sessionManager, RelayClient relayClient) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] args = line.trim().split("\\s+");
                if (args.length == 0) continue;

                String cmd = args[0];
                switch (cmd) {
                    case "open" -> {
                        String sessionId = args.length > 1 ? args[1] : sessionManager.createSession();
                        System.out.println("✅ Открыта сессия: " + sessionId);
                    }
                    case "close" -> {
                        if (args.length < 2) {
                            System.out.println("❌ Укажи sessionId");
                            continue;
                        }
                        String sessionId = args[1];
                        try {
                            sessionManager.closeSession(sessionId);
                        } catch (Exception e) {
                            System.err.println("❌ Ошибка закрытия сессии: " + e.getMessage());
                        }
                    }
                    case "list" -> {
                        var sessions = sessionManager.getAllSessions();
                        if (sessions.isEmpty()) {
                            System.out.println("Нет активных сессий.");
                        } else {
                            sessions.forEach((k, v) -> System.out.println("📌 " + k));
                        }
                    }
                    case "exit" -> {
                        System.out.println("⏹ Остановка приложения...");
                        System.exit(0);
                    }
                    default -> System.out.println("⚠ Неизвестная команда: open | close <id> | list | exit");
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка ввода команд: " + e.getMessage());
        }
    }
}
