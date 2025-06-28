package ru.example.agent;

import javax.net.ssl.SSLSocketFactory;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AgentSessionManager {
    private static final Map<String, Socket> sessions = new ConcurrentHashMap<>();

    public static void openSession(String sessionId, String target) throws Exception {
        if (sessions.containsKey(sessionId)) return;

        String[] parts = target.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);

        Socket socket = (port == 443)
                ? SSLSocketFactory.getDefault().createSocket(host, port)
                : new Socket(host, port);

        sessions.put(sessionId, socket);
    }

    public static void sendData(String sessionId, byte[] data) throws Exception {
        Socket socket = sessions.get(sessionId);
        if (socket == null) throw new IllegalStateException("Session not found");

        OutputStream out = socket.getOutputStream();
        out.write(data);
        out.flush();
    }

    public static byte[] receiveData(String sessionId) throws Exception {
        Socket socket = sessions.get(sessionId);
        if (socket == null) throw new IllegalStateException("Session not found");

        InputStream in = socket.getInputStream();
        byte[] buffer = new byte[4096];
        int read = in.read(buffer);
        if (read == -1) throw new IllegalStateException("Connection closed");

        byte[] result = new byte[read];
        System.arraycopy(buffer, 0, result, 0, read);
        return result;
    }
}
