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

        try {
            relayClient.openSession(sessionId, token);
        } catch (Exception e) {
            throw new RuntimeException("Failed to open session: " + e.getMessage(), e);
        }

        Session session = new Session(sessionId, token);
        sessions.put(ctx, session);
        return session;
    }

    public void closeSession(ChannelHandlerContext ctx) {
        Session session = sessions.remove(ctx);
        if (session != null) {
            try {
                relayClient.closeSession(session.sessionId());
            } catch (Exception e) {
                System.err.println("Failed to close session: " + e.getMessage());
            }
        }
    }

    public record Session(String sessionId, String token) {}

    public void createManualSession(String sessionId) throws IOException {
        String token = TokenUtil.generateToken(sessionId);
        relayClient.openSession(sessionId, token);

        manualSessions.put(sessionId, token);
    }

    public void closeManualSession(String sessionId) throws IOException {
        relayClient.closeSession(sessionId);
        manualSessions.remove(sessionId);
    }

    public Map<String, String> getManualSessions() {
        return manualSessions;
    }
}
