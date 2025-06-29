package ru.example.agent;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class TaskProcessor {

    private final SessionSocketStore socketStore;

    public TaskProcessor(SessionSocketStore socketStore) {
        this.socketStore = socketStore;
    }

    public byte[] process(String sessionId, byte[] commandBytes) throws Exception {
        if (commandBytes.length == 0) {
            throw new IllegalArgumentException("Empty command");
        }

        byte command = commandBytes[0];

        return switch (command) {
            case 0x01 -> handleConnect(sessionId, commandBytes);  // CONNECT
            case 0x02 -> handleSend(sessionId, commandBytes);     // SEND
            case 0x03 -> handleReceive(sessionId);                // RECEIVE
            default -> throw new IllegalArgumentException("Unknown command: " + command);
        };
    }

    private byte[] handleConnect(String sessionId, byte[] data) throws Exception {
        if (data.length < 4) throw new IllegalArgumentException("CONNECT too short");

        int hostLen = data[1] & 0xFF;
        String host = new String(data, 2, hostLen, StandardCharsets.UTF_8);

        int portOffset = 2 + hostLen;
        if (data.length < portOffset + 2) throw new IllegalArgumentException("CONNECT missing port");

        int port = ((data[portOffset] & 0xFF) << 8) | (data[portOffset + 1] & 0xFF);

        socketStore.connect(sessionId, host, port);
        return "CONNECTED".getBytes(StandardCharsets.UTF_8);
    }

    private byte[] handleSend(String sessionId, byte[] data) throws Exception {
        Socket socket = socketStore.getSocket(sessionId);
        if (socket == null) throw new IllegalStateException("Socket not found for session");

        OutputStream out = socket.getOutputStream();
        out.write(data, 1, data.length - 1);
        out.flush();

        return "SENT".getBytes(StandardCharsets.UTF_8);
    }

    private byte[] handleReceive(String sessionId) throws Exception {
        Socket socket = socketStore.getSocket(sessionId);
        if (socket == null) throw new IllegalStateException("Socket not found for session");

        InputStream in = socket.getInputStream();
        byte[] buffer = new byte[4096];
        int read = in.read(buffer);
        if (read == -1) return new byte[0];

        byte[] result = new byte[read];
        System.arraycopy(buffer, 0, result, 0, read);
        return result;
    }
}
