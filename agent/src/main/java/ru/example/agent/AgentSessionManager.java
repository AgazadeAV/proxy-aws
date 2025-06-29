package ru.example.agent;

import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AgentSessionManager {
    private final Map<String, Socket> sessions = new ConcurrentHashMap<>();

    public void store(String sessionId, Socket socket) {
        sessions.put(sessionId, socket);
    }

    public Socket get(String sessionId) {
        return sessions.get(sessionId);
    }

    public void close(String sessionId) {
        try {
            Socket socket = sessions.remove(sessionId);
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) {}
    }
}
