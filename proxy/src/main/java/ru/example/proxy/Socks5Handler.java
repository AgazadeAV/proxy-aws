package ru.example.proxy;

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
            System.out.println("[SOCKS5] Client connected: " + socket.getRemoteSocketAddress());

            if (in.read() != 0x05) {
                System.err.println("Invalid SOCKS version");
                return;
            }

            int nMethods = in.read();
            for (int i = 0; i < nMethods; i++) in.read(); // consume methods
            out.write(new byte[]{0x05, 0x00}); // no auth

            // Read request
            if (in.read() != 0x05 || in.read() != 0x01) { // version + command
                System.err.println("Invalid request");
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
                return;
            }

            int port = (in.read() << 8) | in.read();
            String target = host + ":" + port;
            System.out.println("[SOCKS5] Target → " + target);

            out.write(new byte[]{0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0});

            Optional<Map.Entry<String, String>> optionalSession = sessionManager.getAvailableSession();
            if (optionalSession.isEmpty()) {
                System.err.println("No active session available");
                return;
            }

            Map.Entry<String, String> session = optionalSession.get();
            String sessionId = session.getKey();
            String token = session.getValue();

            Thread sender = new Thread(() -> {
                try {
                    byte[] buf = new byte[4096];
                    int read;
                    while ((read = in.read(buf)) != -1) {
                        byte[] data = new byte[read];
                        System.arraycopy(buf, 0, data, 0, read);
                        relayClient.sendPayload(sessionId, token, target, data);
                    }
                } catch (Exception e) {
                    System.err.println("[Sender] " + e.getMessage());
                }
            });

            Thread receiver = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        byte[] response = relayClient.fetchPayload(sessionId);
                        if (response.length > 0) {
                            out.write(response);
                            out.flush();
                        } else {
                            Thread.sleep(100);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[Receiver] " + e.getMessage());
                }
            });

            sender.start();
            receiver.start();
            sender.join();
            receiver.interrupt();
            relayClient.closeSession(sessionId);

        } catch (Exception e) {
            System.err.println("[SOCKS5] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
