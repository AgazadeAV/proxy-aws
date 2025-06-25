package ru.example.proxy;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyApp {
    private static final int PORT = 1080;
    private static final String ENQUEUE_URL = "https://sbbd0vxjdj.execute-api.us-east-2.amazonaws.com/prod/enqueue";
    private static final String RESULT_URL = "https://sbbd0vxjdj.execute-api.us-east-2.amazonaws.com/prod/relay-result";
    private static final String NOTIFY_URL = "https://sbbd0vxjdj.execute-api.us-east-2.amazonaws.com/prod/poll"; // новое
    private static final String AGENT_ID = "agent-001";

    private static final OkHttpClient client = new OkHttpClient();

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("[SOCKS5] Proxy started on port " + PORT);
        ExecutorService executor = Executors.newFixedThreadPool(50);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            executor.submit(() -> handleClient(clientSocket));
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (InputStream in = clientSocket.getInputStream(); OutputStream out = clientSocket.getOutputStream()) {

            // SOCKS5 handshake
            if (in.read() != 0x05) return;
            int nMethods = in.read();
            in.skip(nMethods);
            out.write(new byte[]{0x05, 0x00});

            // SOCKS5 CONNECT
            if (in.read() != 0x05 || in.read() != 0x01) return;
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
            String sessionId = UUID.randomUUID().toString();

            System.out.printf("[Proxy] Session %s → %s%n", sessionId, target);

            // 🔔 Уведомить агента (POST /poll) — чтобы агент знал sessionId
            try {
                String notifyJson = String.format("{\"agentId\":\"%s\",\"sessionId\":\"%s\"}", AGENT_ID, sessionId);
                Request notifyRequest = new Request.Builder()
                        .url(NOTIFY_URL)
                        .post(RequestBody.create(notifyJson, MediaType.get("application/json")))
                        .build();

                client.newCall(notifyRequest).execute().close();
                System.out.println("[Proxy] Notified agent for session: " + sessionId);
            } catch (Exception e) {
                System.err.println("[Proxy] Failed to notify agent: " + e.getMessage());
            }

            out.write(new byte[]{0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0});

            // Read -> /enqueue
            Thread sender = new Thread(() -> {
                try {
                    byte[] buf = new byte[4096];
                    int read;
                    while ((read = in.read(buf)) != -1) {
                        byte[] actual = new byte[read];
                        System.arraycopy(buf, 0, actual, 0, read);
                        String payload = Base64.getEncoder().encodeToString(actual);
                        String json = String.format("{\"agentId\":\"%s\",\"sessionId\":\"%s\",\"target\":\"%s\",\"payload\":\"%s\"}", AGENT_ID, sessionId, target, payload);

                        Request request = new Request.Builder()
                                .url(ENQUEUE_URL)
                                .post(RequestBody.create(json, MediaType.get("application/json")))
                                .build();

                        client.newCall(request).execute().close();
                    }
                } catch (Exception e) {
                    System.err.println("[Sender] Error: " + e.getMessage());
                }
            });

            // Poll -> /relay-result
            Thread receiver = new Thread(() -> {
                try {
                    while (true) {
                        Request poll = new Request.Builder()
                                .url(RESULT_URL + "?sessionId=" + sessionId)
                                .get().build();

                        try (Response response = client.newCall(poll).execute()) {
                            if (response.code() == 200 && response.body() != null) {
                                byte[] decoded = Base64.getDecoder().decode(response.body().string());
                                out.write(decoded);
                                out.flush();
                            }
                        }

                        Thread.sleep(100);
                    }
                } catch (Exception e) {
                    System.err.println("[Receiver] Error: " + e.getMessage());
                }
            });

            sender.start();
            receiver.start();
            sender.join();
            receiver.interrupt();

        } catch (Exception e) {
            System.err.println("[SOCKS5] Client error: " + e.getMessage());
        }
    }
}
