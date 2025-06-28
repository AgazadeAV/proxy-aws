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
        System.out.println("[SessionManager] Initialized.");
    }

    public String createSession() {
        try {
            String sessionId = UUID.randomUUID().toString();
            String token = TokenUtil.generateToken(sessionId);

            System.out.printf("[createSession] Creating session: sessionId=%s token=%s%n", sessionId, token);

            relayClient.openSession(sessionId, token);
            sessions.put(sessionId, token);

            System.out.printf("[createSession] ✅ Session created: %s%n", sessionId);
            return sessionId;
        } catch (Exception e) {
            System.err.println("[createSession] ❌ Failed to create session: " + e.getMessage());
            throw new RuntimeException("Failed to create session", e);
        }
    }

    public void closeSession(String sessionId) throws Exception {
        String token = sessions.remove(sessionId);

        System.out.printf("[closeSession] Attempting to close session: sessionId=%s token=%s%n", sessionId, token);

        if (token != null) {
            relayClient.closeSession(sessionId);
            System.out.printf("[closeSession] ✅ Session closed: %s%n", sessionId);
        } else {
            System.out.printf("[closeSession] ⚠️ Session not found: %s%n", sessionId);
        }
    }

    public Optional<Map.Entry<String, String>> getAvailableSession() {
        Optional<Map.Entry<String, String>> sessionOpt = sessions.entrySet().stream().findFirst();
        if (sessionOpt.isPresent()) {
            Map.Entry<String, String> entry = sessionOpt.get();
            System.out.printf("[getAvailableSession] Found available session: sessionId=%s token=%s%n", entry.getKey(), entry.getValue());
        } else {
            System.out.println("[getAvailableSession] ❌ No available sessions.");
        }
        return sessionOpt;
    }

    public Map<String, String> getAllSessions() {
        System.out.println("[getAllSessions] Current sessions: " + sessions.keySet());
        return sessions;
    }
}
