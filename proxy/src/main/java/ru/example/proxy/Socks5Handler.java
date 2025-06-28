package ru.example.proxy;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Map;
import java.util.Optional;

public class Socks5Handler implements Runnable {
    private final Socket socket;
    private final SessionManager sessionManager;
    private final RelayClient relayClient;

    public Socks5Handler(Socket socket, SessionManager sessionManager, RelayClient relayClient) {
        this.socket = socket;
        this.sessionManager = sessionManager;
        this.relayClient = relayClient;
    }

    @Override
    public void run() {
        try (InputStream in = socket.getInputStream(); OutputStream out = socket.getOutputStream()) {
            System.out.println("[SOCKS5] 📡 Client connected: " + socket.getRemoteSocketAddress());

            if (in.read() != 0x05) {
                System.err.println("[SOCKS5] ❌ Invalid SOCKS version");
                return;
            }

            int nMethods = in.read();
            for (int i = 0; i < nMethods; i++) in.read(); // consume methods
            out.write(new byte[]{0x05, 0x00}); // no auth
            System.out.println("[SOCKS5] ✅ Auth negotiation completed");

            int version = in.read();
            int cmd = in.read();
            if (version != 0x05 || cmd != 0x01) {
                System.err.printf("[SOCKS5] ❌ Invalid request: version=%02x, cmd=%02x%n", version, cmd);
                return;
            }

            in.read(); // reserved
            int atyp = in.read();
            String host;
            if (atyp == 0x01) {
                host = String.format("%d.%d.%d.%d", in.read() & 0xFF, in.read() & 0xFF, in.read() & 0xFF, in.read() & 0xFF);
            } else if (atyp == 0x03) {
                int len = in.read();
                byte[] domain = in.readNBytes(len);
                host = new String(domain);
            } else {
                out.write(new byte[]{0x05, 0x08, 0x00, 0x01});
                System.err.println("[SOCKS5] ❌ Unsupported address type");
                return;
            }

            int port = (in.read() << 8) | in.read();
            String target = host + ":" + port;
            System.out.println("[SOCKS5] 🎯 Target: " + target);

            out.write(new byte[]{0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0});
            System.out.println("[SOCKS5] ✅ Sent connection success reply to client");

            Optional<Map.Entry<String, String>> optionalSession = sessionManager.getAvailableSession();
            if (optionalSession.isEmpty()) {
                System.err.println("[SOCKS5] ❌ No active session available");
                return;
            }

            Map.Entry<String, String> session = optionalSession.get();
            String sessionId = session.getKey();
            String token = session.getValue();
            System.out.printf("[SOCKS5] 🔐 Using session: %s%n", sessionId);

            try {
                ByteArrayOutputStream payload = new ByteArrayOutputStream();
                payload.write(0x01);
                payload.write(host.length());
                payload.write(host.getBytes());
                payload.write((port >> 8) & 0xFF);
                payload.write(port & 0xFF);

                System.out.println("[CONNECT] 📤 Sending CONNECT payload to agent");
                relayClient.sendPayload(sessionId, token, payload.toByteArray());
            } catch (Exception e) {
                System.err.println("[CONNECT] ❌ Failed to send connect payload: " + e.getMessage());
                return;
            }

            Thread sender = new Thread(() -> {
                try {
                    byte[] buf = new byte[4096];
                    int read;
                    while ((read = in.read(buf)) != -1) {
                        byte[] data = new byte[read];
                        System.arraycopy(buf, 0, data, 0, read);
                        System.out.printf("[Sender] 🔼 Forwarding %d bytes to agent%n", read);

                        ByteArrayOutputStream payload = new ByteArrayOutputStream();
                        payload.write(0x02);
                        payload.write(data);
                        relayClient.sendPayload(sessionId, token, payload.toByteArray());
                    }
                    System.out.println("[Sender] 🔚 Client stream ended");
                } catch (Exception e) {
                    System.err.println("[Sender] ❌ Error: " + e.getMessage());
                }
            });

            Thread receiver = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        ByteArrayOutputStream payload = new ByteArrayOutputStream();
                        payload.write(0x03);
                        relayClient.sendPayload(sessionId, token, payload.toByteArray());

                        byte[] response = relayClient.fetchPayload(sessionId);
                        if (response.length > 0) {
                            System.out.printf("[Receiver] 🔽 Received %d bytes from agent%n", response.length);
                            out.write(response);
                            out.flush();
                        } else {
                            Thread.sleep(100);
                        }
                    }
                    System.out.println("[Receiver] 🔚 Thread interrupted");
                } catch (Exception e) {
                    System.err.println("[Receiver] ❌ Error: " + e.getMessage());
                }
            });

            sender.start();
            receiver.start();
            sender.join();
            receiver.interrupt();
            relayClient.closeSession(sessionId);
            System.out.println("[SOCKS5] ✅ Session closed cleanly: " + sessionId);

        } catch (Exception e) {
            System.err.println("[SOCKS5] ❌ Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
