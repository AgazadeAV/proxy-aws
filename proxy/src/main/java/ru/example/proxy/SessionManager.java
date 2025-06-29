package ru.example.proxy;

import java.util.Base64;
import java.util.Scanner;
import java.util.UUID;

public class SessionManager {

    private final RelayClient relayClient;
    private String sessionId;
    private String token;

    public SessionManager(RelayClient relayClient) {
        this.relayClient = relayClient;
        listenForOpenCommand();
    }

    private void listenForOpenCommand() {
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.print("> ");
                String cmd = scanner.nextLine();
                if ("open".equalsIgnoreCase(cmd)) {
                    this.sessionId = UUID.randomUUID().toString();
                    this.token = Base64.getEncoder().encodeToString(sessionId.getBytes());
                    System.out.println("[SESSION CREATED]");
                    System.out.println("Session ID: " + sessionId);
                    System.out.println("Token: " + token);

                    String json = String.format("{\"sessionId\":\"%s\",\"token\":\"%s\"}", sessionId, token);
                    relayClient.enqueueTask(sessionId, json); // or use dedicated /session/open if needed
                }
            }
        }).start();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getToken() {
        return token;
    }
}
