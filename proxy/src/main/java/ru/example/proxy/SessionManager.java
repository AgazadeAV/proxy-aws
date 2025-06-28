package ru.example.proxy;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
    private final RelayClient relayClient;
    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    public SessionManager(RelayClient relayClient) {
        this.relayClient = relayClient;
    }

    public String createSession() {
        try {
            String sessionId = UUID.randomUUID().toString();
            String token = TokenUtil.generateToken(sessionId);

            relayClient.openSession(sessionId, token);
            sessions.put(sessionId, token);

            System.out.println("✅ Session created: " + sessionId);
            return sessionId;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create session", e);
        }
    }

    public void closeSession(String sessionId) throws Exception {
        String token = sessions.remove(sessionId);
        if (token != null) {
            relayClient.closeSession(sessionId);
            System.out.println("✅ Session closed: " + sessionId);
        } else {
            System.out.println("⚠️ Session not found: " + sessionId);
        }
    }

    public Optional<Map.Entry<String, String>> getAvailableSession() {
        return sessions.entrySet().stream().findFirst();
    }

    public Map<String, String> getAllSessions() {
        return sessions;
    }
}
