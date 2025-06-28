package ru.example.agent;

import java.io.ByteArrayInputStream;

public class AgentLogic {

    public static byte[] process(String sessionId, byte[] payload) throws Exception {
        // 🎯 Пример: первая команда — CONNECT, следующая — байты
        ByteArrayInputStream in = new ByteArrayInputStream(payload);
        int command = in.read();

        switch (command) {
            case 0x01 -> { // CONNECT
                byte[] hostBytes = new byte[in.read()];
                in.read(hostBytes);
                String host = new String(hostBytes);

                int port = (in.read() << 8) | in.read();
                AgentSessionManager.connect(sessionId, host, port);
                return "OK".getBytes();
            }

            case 0x02 -> { // SEND
                byte[] data = in.readAllBytes();
                AgentSessionManager.sendData(sessionId, data);
                return "SENT".getBytes();
            }

            case 0x03 -> { // RECEIVE
                return AgentSessionManager.receiveData(sessionId);
            }

            default -> throw new IllegalArgumentException("Unknown command");
        }
    }
}
