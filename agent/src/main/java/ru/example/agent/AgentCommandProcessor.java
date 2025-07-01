package ru.example.agent;

import lombok.RequiredArgsConstructor;
import ru.example.agent.dto.CommandMessage;
import ru.example.agent.dto.ConnectCommand;
import ru.example.agent.dto.SendCommand;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Base64;

@RequiredArgsConstructor
public class AgentCommandProcessor {

    private final AgentSessionManager sessionManager;
    private final AgentRelayClient relayClient;

    public void process(CommandMessage task, String sessionId) {
        try {
            switch (task.command) {
                case "CONNECT" -> {
                    ConnectCommand connect = (ConnectCommand) task;
                    handleConnect(sessionId, connect.getAddress(), connect.getPort());
                }
                case "SEND" -> {
                    SendCommand send = (SendCommand) task;
                    handleSend(sessionId, getPayloadBytes(send.getPayload()));
                }
                case "RECEIVE" -> handleReceive(sessionId);
                default -> System.err.println("[Processor] Unknown command: " + task.command);
            }
        } catch (Exception e) {
            System.err.println("[Processor] Error handling task: " + e.getMessage());
        }
    }

    private void handleConnect(String sessionId, String host, int port) throws Exception {
        System.out.printf("[CONNECT] Connecting to %s:%d for session %s%n", host, port, sessionId);
        Socket socket = new Socket(host, port);
        sessionManager.store(sessionId, socket);
    }

    private void handleSend(String sessionId, byte[] data) throws Exception {
        Socket socket = sessionManager.get(sessionId);
        if (socket == null) {
            System.err.println("[SEND] No socket found for session " + sessionId);
            return;
        }
        OutputStream out = socket.getOutputStream();
        out.write(data);
        out.flush();
        System.out.println("[SEND] Sent " + data.length + " bytes");
    }

    private void handleReceive(String sessionId) throws Exception {
        Socket socket = sessionManager.get(sessionId);
        if (socket == null) {
            System.err.println("[RECEIVE] No socket found for session " + sessionId);
            return;
        }
        InputStream in = socket.getInputStream();
        byte[] buffer = new byte[4096];
        int read = in.read(buffer);
        if (read > 0) {
            byte[] result = new byte[read];
            System.arraycopy(buffer, 0, result, 0, read);
            String payload = Base64.getEncoder().encodeToString(result);
            relayClient.submitResult(sessionId, payload);
            System.out.println("[RECEIVE] Read and submitted " + read + " bytes");
        } else {
            System.out.println("[RECEIVE] No data available");
        }
    }

    public byte[] getPayloadBytes(String payload) {
        if (payload == null || payload.isBlank()) return new byte[0];
        return Base64.getDecoder().decode(payload);
    }
}
