package ru.example.proxy;

import io.netty.channel.ChannelHandlerContext;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private final RelayClient relayClient;
    private final Map<String, String> manualSessions = new ConcurrentHashMap<>();
    private final Map<ChannelHandlerContext, Session> sessions = new ConcurrentHashMap<>();

    public SessionManager(RelayClient relayClient) {
        this.relayClient = relayClient;
    }

    public Session createSession(ChannelHandlerContext ctx) {
        String sessionId = UUID.randomUUID().toString();
        String token = TokenUtil.generateToken(sessionId);

        System.out.printf("[SessionManager] Creating session: sessionId=%s, token=%s%n", sessionId, token);

        try {
            relayClient.openSession(sessionId, token);
            System.out.printf("[SessionManager] Session opened: %s%n", sessionId);
        } catch (Exception e) {
            System.err.printf("[SessionManager] Failed to open session %s: %s%n", sessionId, e.getMessage());
            throw new RuntimeException("Failed to open session: " + e.getMessage(), e);
        }

        Session session = new Session(sessionId, token);
        sessions.put(ctx, session);
        return session;
    }

    public void closeSession(ChannelHandlerContext ctx) {
        Session session = sessions.remove(ctx);
        if (session != null) {
            System.out.printf("[SessionManager] Closing session: %s%n", session.sessionId());
            try {
                relayClient.closeSession(session.sessionId());
                System.out.printf("[SessionManager] Session closed: %s%n", session.sessionId());
            } catch (Exception e) {
                System.err.printf("[SessionManager] Failed to close session %s: %s%n", session.sessionId(), e.getMessage());
            }
        } else {
            System.out.println("[SessionManager] No session found to close for context");
        }
    }

    public void createManualSession(String sessionId) throws IOException {
        String token = TokenUtil.generateToken(sessionId);
        System.out.printf("[SessionManager] Creating manual session: sessionId=%s, token=%s%n", sessionId, token);
        relayClient.openSession(sessionId, token);
        manualSessions.put(sessionId, token);
        System.out.printf("[SessionManager] Manual session added: %s%n", sessionId);
    }

    public void closeManualSession(String sessionId) throws IOException {
        System.out.printf("[SessionManager] Closing manual session: %s%n", sessionId);
        relayClient.closeSession(sessionId);
        manualSessions.remove(sessionId);
        System.out.printf("[SessionManager] Manual session removed: %s%n", sessionId);
    }

    public Map<String, String> getManualSessions() {
        return manualSessions;
    }

    public record Session(String sessionId, String token) {}
}
