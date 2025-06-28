package ru.example.agent;

import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AgentSessionManager {
    private static final Map<String, Socket> sessions = new ConcurrentHashMap<>();

    public static void connect(String sessionId, String host, int port) throws Exception {
        if (sessions.containsKey(sessionId)) {
            System.out.printf("[AgentSessionManager] Session already exists: %s%n", sessionId);
            return;
        }

        System.out.printf("[AgentSessionManager] Connecting to %s:%d (session: %s)%n", host, port, sessionId);
        Socket socket = (port == 443)
                ? SSLSocketFactory.getDefault().createSocket(host, port)
                : new Socket(host, port);

        sessions.put(sessionId, socket);
        System.out.printf("[AgentSessionManager] ✅ Connected and stored session: %s%n", sessionId);
    }

    public static void sendData(String sessionId, byte[] data) throws Exception {
        Socket socket = sessions.get(sessionId);
        if (socket == null) {
            System.err.printf("[AgentSessionManager] ❌ Session not found for send: %s%n", sessionId);
            throw new IllegalStateException("Session not found: " + sessionId);
        }

        OutputStream out = socket.getOutputStream();
        out.write(data);
        out.flush();
        System.out.printf("[AgentSessionManager] ⇢ Sent %d bytes (session: %s)%n", data.length, sessionId);
    }

    public static byte[] receiveData(String sessionId) throws Exception {
        Socket socket = sessions.get(sessionId);
        if (socket == null) {
            System.err.printf("[AgentSessionManager] ❌ Session not found for receive: %s%n", sessionId);
            throw new IllegalStateException("Session not found: " + sessionId);
        }

        socket.setSoTimeout(3000);
        InputStream in = socket.getInputStream();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096];

        System.out.printf("[AgentSessionManager] ⇠ Receiving data (session: %s)%n", sessionId);

        while (true) {
            try {
                int read = in.read(tmp);
                if (read == -1) break;
                buffer.write(tmp, 0, read);
            } catch (SocketTimeoutException e) {
                System.out.printf("[AgentSessionManager] ⏱️ Timeout while receiving (session: %s)%n", sessionId);
                break;
            }
        }

        byte[] result = buffer.toByteArray();
        System.out.println("[AgentSessionManager] RAW: " + buffer.toString(StandardCharsets.UTF_8));
        System.out.printf("[AgentSessionManager] ⬅ Received %d bytes (session: %s)%n", result.length, sessionId);
        return result;
    }
}
