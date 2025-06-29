package ru.example.agent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionSocketStore {

    private final Map<String, Socket> sockets = new ConcurrentHashMap<>();

    public void connect(String sessionId, String host, int port) throws IOException {
        if (sockets.containsKey(sessionId)) {
            System.err.printf("[SocketStore] ⚠ Socket already exists for session: %s%n", sessionId);
            throw new IllegalStateException("Socket already exists for session: " + sessionId);
        }

        System.out.printf("[SocketStore] 🌐 Connecting to %s:%d for session: %s%n", host, port, sessionId);

        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 3000); // 3s timeout

        System.out.printf("[SocketStore] ✅ Connection established for session: %s%n", sessionId);

        sockets.put(sessionId, socket);
    }

    public Socket getSocket(String sessionId) {
        Socket socket = sockets.get(sessionId);
        if (socket == null) {
            System.err.printf("[SocketStore] ❌ No socket found for session: %s%n", sessionId);
        } else {
            System.out.printf("[SocketStore] 🔍 Socket retrieved for session: %s%n", sessionId);
        }
        return socket;
    }

    public void closeAll() {
        System.out.println("[SocketStore] 🛑 Closing all sockets...");
        for (Map.Entry<String, Socket> entry : sockets.entrySet()) {
            try {
                entry.getValue().close();
                System.out.printf("[SocketStore] ✅ Closed socket for session: %s%n", entry.getKey());
            } catch (IOException e) {
                System.err.printf("[SocketStore] ❌ Failed to close socket for session %s: %s%n", entry.getKey(), e.getMessage());
            }
        }
        sockets.clear();
    }
}
