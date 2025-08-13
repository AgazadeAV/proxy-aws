package org.example.agent;

import org.example.webrtc.Transport;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.*;

public class StreamRouter implements Transport.Listener {

    private final String sessionId;
    private final Transport transport;
    private final TcpDialer dialer;

    private final Map<Integer, Socket> sockets = new ConcurrentHashMap<>();
    private final ExecutorService readers = Executors.newCachedThreadPool();

    public StreamRouter(String sessionId, Transport transport, TcpDialer dialer) {
        this.sessionId = sessionId;
        this.transport = transport;
        this.dialer = dialer;
    }

    public void shutdown() {
        readers.shutdownNow();
        sockets.values().forEach(s -> { try { s.close(); } catch (Exception ignored) {} });
        sockets.clear();
    }

    /* ===== Transport.Listener ===== */

    @Override
    public void onConnectAck(int streamId, boolean ok, String message) {
        // На агенте ack обычному клиенту не нужен — это обратный канал к controller.
        // Ничего не делаем.
    }

    @Override
    public void onData(int streamId, byte[] data) {
        Socket s = sockets.get(streamId);
        if (s == null) return;
        try {
            OutputStream out = s.getOutputStream();
            out.write(data);
            out.flush();
        } catch (Exception e) {
            closeStream(streamId, "write-error: " + e.getMessage());
        }
    }

    @Override
    public void onClose(int streamId, String reason) {
        closeStream(streamId, "remote-close: " + reason);
    }

    @Override
    public void onLog(String msg) {
        System.out.println("[Agent] " + msg);
    }

    /* ===== Вспомогательные вызовы сверху (после WebRTC) =====
       Когда внедрим реальный транспорт, сюда будет прилетать CONNECT.
       В нашем API транспорта CONNECT инициирует controller через transport.open().
       На агенте это проявится как входящее сообщение (внутри WebRTC-реализации),
       и мы должны будем открыть сокет. Для MVP с FakeTransport CONNECT не прилетает.
     */

    /** Открыть локальный TCP и стартовать reader-loop (агентская сторона). */
    public void onIncomingConnect(int streamId, String host, int port) {
        try {
            Socket s = dialer.connect(host, port);
            sockets.put(streamId, s);

            // reader-loop: из TCP -> transport.DATA
            readers.submit(() -> {
                try (Socket sock = s; InputStream in = sock.getInputStream()) {
                    byte[] buf = new byte[16 * 1024];
                    int r;
                    while ((r = in.read(buf)) != -1) {
                        byte[] chunk = new byte[r];
                        System.arraycopy(buf, 0, chunk, 0, r);
                        transport.send(streamId, chunk);
                    }
                } catch (Exception e) {
                    // TCP закрылся → уведомим контроллер
                } finally {
                    transport.close(streamId, "tcp-closed");
                    sockets.remove(streamId);
                }
            });

            // отправить ACK контроллеру
            transport.send(streamId, new byte[0]); // Заглушка; в реальном WebRTC отправим CONNECT_ACK ok=true

        } catch (Exception e) {
            // ACK fail
            transport.close(streamId, "connect-failed: " + e.getMessage());
        }
    }

    private void closeStream(int streamId, String why) {
        Socket s = sockets.remove(streamId);
        if (s != null) {
            try { s.close(); } catch (Exception ignored) {}
        }
    }
}
