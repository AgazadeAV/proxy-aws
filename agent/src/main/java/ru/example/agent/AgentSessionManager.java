package ru.example.agent;

import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
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

        socket.setSoTimeout(2000); // 2 секунды timeout на read

        InputStream in = socket.getInputStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];

        try {
            while (true) {
                int read = in.read(buffer);
                if (read == -1) break; // сокет закрыт
                baos.write(buffer, 0, read);

                // если данных пока нет — выйдем после таймаута
                if (in.available() == 0) break;
            }
        } catch (java.net.SocketTimeoutException e) {
            // читаем всё, что успело прийти
        }

        return baos.toByteArray();
    }

}
