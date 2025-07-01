package ru.example.proxy;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;

import java.util.Base64;

@RequiredArgsConstructor
public class ResultReader implements Runnable {

    private final ProxyRelayClient relayClient;
    private final String sessionId;
    private final ChannelHandlerContext ctx;

    private volatile boolean running = true;

    public void stop() {
        running = false;
    }

    @Override
    @SuppressWarnings("BusyWait")
    public void run() {
        System.out.println("[ResultReader] Started for session: " + sessionId);
        while (running && ctx.channel().isActive()) {
            try {
                String json = relayClient.fetchResult(sessionId);
                if (json != null && !json.isBlank()) {
                    String payload = extractPayload(json);
                    if (payload != null) {
                        byte[] bytes = Base64.getDecoder().decode(payload);
                        ctx.writeAndFlush(Unpooled.wrappedBuffer(bytes));
                        System.out.println("[ResultReader] Received " + bytes.length + " bytes from agent");
                    }
                }
                Thread.sleep(300); // polling interval
            } catch (Exception e) {
                System.err.println("[ResultReader] Error: " + e.getMessage());
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        }
        System.out.println("[ResultReader] Stopped for session: " + sessionId);
    }

    private String extractPayload(String json) {
        try {
            // Пример: {"payload": "dGVzdC1yZXNwb25zZQ=="}
            int keyIndex = json.indexOf("\"payload\":");
            if (keyIndex == -1) return null;

            int start = json.indexOf("\"", keyIndex + 9); // первая кавычка после ":"
            int end = json.indexOf("\"", start + 1);      // вторая кавычка

            if (start == -1 || end == -1) return null;

            return json.substring(start + 1, end);
        } catch (Exception e) {
            System.err.println("[extractPayload] Error: " + e.getMessage());
            return null;
        }
    }
}
