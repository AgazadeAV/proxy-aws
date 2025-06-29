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
            throw new IllegalStateException("Socket already exists for session: " + sessionId);
        }
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 3000); // 3s timeout
        sockets.put(sessionId, socket);
    }

    public Socket getSocket(String sessionId) {
        return sockets.get(sessionId);
    }
}
