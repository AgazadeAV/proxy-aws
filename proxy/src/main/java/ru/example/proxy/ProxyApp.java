package ru.example.proxy;

import ru.example.shared.SessionTokenUtil;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyApp {
    private static final int PORT = 1080;
    private static final Map<String, String> sessions = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        System.out.println("[SOCKS5] Proxy started on port " + PORT);
        ExecutorService executor = Executors.newFixedThreadPool(50);

        // CLI поток
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (true) {
                String line = scanner.nextLine().trim();
                if (line.equalsIgnoreCase("create")) {
                    try {
                        String sessionId = UUID.randomUUID().toString();
                        String token = SessionTokenUtil.generateToken(sessionId);
                        System.out.println("[CLI] Creating session " + sessionId);
                        ProxySessionHandler.openSession(sessionId, token);
                        sessions.put(sessionId, token);
                        System.out.println("[SESSION CREATED]");
                        System.out.println("Session ID: " + sessionId);
                        System.out.println("Token: " + token);
                    } catch (Exception e) {
                        System.err.println("[CLI] Failed to create session: " + e.getMessage());
                        e.printStackTrace();
                    }
                } else if (line.startsWith("close")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length < 2) {
                        System.out.println("Usage: close <sessionId>");
                        continue;
                    }
                    String sessionId = parts[1];
                    String token = sessions.remove(sessionId);
                    if (token != null) {
                        System.out.println("[CLI] Closing session " + sessionId);
                        ProxySessionHandler.close(sessionId, token);
                    } else {
                        System.out.println("No such session: " + sessionId);
                    }
                }
            }
        }).start();

        // SOCKS сервер
        ServerSocket serverSocket = new ServerSocket(PORT);
        while (true) {
            Socket clientSocket = serverSocket.accept();
            executor.submit(() -> handleClient(clientSocket));
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (InputStream in = clientSocket.getInputStream();
             OutputStream out = clientSocket.getOutputStream()) {

            System.out.println("[SOCKS5] New client connected");

            if (in.read() != 0x05) {
                System.err.println("[SOCKS5] Invalid SOCKS version");
                return;
            }

            int nMethods = in.read();
            for (int i = 0; i < nMethods; i++) in.read(); // consume methods
            out.write(new byte[]{0x05, 0x00});

            if (in.read() != 0x05 || in.read() != 0x01) {
                System.err.println("[SOCKS5] Invalid request version or command");
                return;
            }

            in.read(); // reserved
            int atyp = in.read();
            String host;
            if (atyp == 0x01) {
                host = String.format("%d.%d.%d.%d",
                        in.read() & 0xFF, in.read() & 0xFF, in.read() & 0xFF, in.read() & 0xFF);
            } else if (atyp == 0x03) {
                int len = in.read();
                byte[] domain = in.readNBytes(len);
                host = new String(domain);
            } else {
                out.write(new byte[]{0x05, 0x08, 0x00, 0x01});
                System.err.println("[SOCKS5] Unsupported address type");
                return;
            }

            int port = (in.read() << 8) | in.read();
            String target = host + ":" + port;
            System.out.println("[Proxy] → " + target);

            out.write(new byte[]{0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0});

            Map.Entry<String, String> entry = sessions.entrySet().stream().findFirst().orElse(null);
            if (entry == null) {
                System.err.println("[Proxy] No available sessions");
                return;
            }

            String sessionId = entry.getKey();
            String token = entry.getValue();
            System.out.println("[Proxy] Using session: " + sessionId);

            Thread sender = new Thread(() -> {
                try {
                    byte[] buf = new byte[4096];
                    int read;
                    while ((read = in.read(buf)) != -1) {
                        byte[] actual = new byte[read];
                        System.arraycopy(buf, 0, actual, 0, read);
                        ProxySessionHandler.send(sessionId, token, target, actual);
                        System.out.println("[Sender] Sent " + read + " bytes");
                    }
                } catch (Exception e) {
                    System.err.println("[Sender] Error: " + e.getMessage());
                    e.printStackTrace();
                }
            });

            Thread receiver = new Thread(() -> {
                try {
                    int emptyCount = 0;
                    while (true) {
                        byte[] data = ProxySessionHandler.receive(sessionId, token);
                        if (data.length > 0) {
                            System.out.println("[Receiver] Received " + data.length + " bytes");
                            out.write(data);
                            out.flush();
                            emptyCount = 0;
                        } else {
                            emptyCount++;
                            if (emptyCount > 300) {
                                System.out.println("[Receiver] Timeout — no response in 30s");
                                break;
                            }
                        }
                        Thread.sleep(100);
                    }
                } catch (Exception e) {
                    System.err.println("[Receiver] Error: " + e.getMessage());
                    e.printStackTrace();
                }
            });

            sender.start();
            receiver.start();
            sender.join();
            ProxySessionHandler.close(sessionId, token);
            receiver.interrupt();

        } catch (Exception e) {
            System.err.println("[SOCKS5] Client error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
