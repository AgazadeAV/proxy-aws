package ru.example.agent;

import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AgentSessionManager {
    private static final Map<String, Socket> sessions = new ConcurrentHashMap<>();

    public static void connect(String sessionId, String host, int port) throws Exception {
        if (sessions.containsKey(sessionId)) return;

        Socket socket = (port == 443)
                ? SSLSocketFactory.getDefault().createSocket(host, port)
                : new Socket(host, port);

        sessions.put(sessionId, socket);
    }

    public static void sendData(String sessionId, byte[] data) throws Exception {
        Socket socket = sessions.get(sessionId);
        if (socket == null) throw new IllegalStateException("Session not found: " + sessionId);

        OutputStream out = socket.getOutputStream();
        out.write(data);
        out.flush();
    }

    public static byte[] receiveData(String sessionId) throws Exception {
        Socket socket = sessions.get(sessionId);
        if (socket == null) throw new IllegalStateException("Session not found: " + sessionId);

        socket.setSoTimeout(1000); // 1 секунда — максимум, сколько ждём ответ

        InputStream in = socket.getInputStream();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096];

        while (true) {
            try {
                int read = in.read(tmp);
                if (read == -1) break;
                buffer.write(tmp, 0, read);
            } catch (SocketTimeoutException e) {
                // Нет данных за 1 секунду — считаем, что всё
                break;
            }
        }

        return buffer.toByteArray();
    }

    public static boolean hasSession(String sessionId) {
        return sessions.containsKey(sessionId);
    }
}
