package ru.example.agent;

import java.io.ByteArrayInputStream;

public class AgentLogic {

    public static byte[] process(String sessionId, byte[] payload) throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream(payload);
        int command = in.read();

        switch (command) {
            case 0x01 -> { // CONNECT
                int hostLength = in.read();
                byte[] hostBytes = new byte[hostLength];
                in.read(hostBytes);
                String host = new String(hostBytes);

                int port = (in.read() << 8) | in.read();
                System.out.printf("[AgentLogic] CONNECT → %s:%d (session: %s)%n", host, port, sessionId);
                AgentSessionManager.connect(sessionId, host, port);
                return "OK".getBytes();
            }

            case 0x02 -> { // SEND
                byte[] data = in.readAllBytes();
                System.out.printf("[AgentLogic] SEND → %d bytes (session: %s)%n", data.length, sessionId);
                AgentSessionManager.sendData(sessionId, data);
                return "SENT".getBytes();
            }

            case 0x03 -> { // RECEIVE
                System.out.printf("[AgentLogic] RECEIVE request (session: %s)%n", sessionId);
                byte[] received = AgentSessionManager.receiveData(sessionId);
                System.out.printf("[AgentLogic] RECEIVED ← %d bytes (session: %s)%n", received.length, sessionId);
                return received;
            }

            default -> {
                System.err.printf("[AgentLogic] ❌ Unknown command: 0x%02X (session: %s)%n", command, sessionId);
                throw new IllegalArgumentException("Unknown command");
            }
        }
    }
}
