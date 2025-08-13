package org.example.webrtc;

/**
 * Простая "фейковая" реализация, чтобы прогнать всю остальную логику
 * (SOCKS5/роутинг/конфиги) до того, как мы подключим JNI к libwebrtc.
 *
 * Поведение:
 *  - start/stop: только лог.
 *  - open(): сразу шлёт onConnectAck(streamId, false, "FakeTransport")
 *  - send()/close(): лог + onClose.
 */
public class FakeTransport implements Transport {

    private Listener listener;
    private String sessionId = "unknown";

    @Override
    public void setListener(Listener listener) { this.listener = listener; }

    @Override
    public void setSessionId(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) this.sessionId = sessionId;
    }

    @Override
    public void start() { log("[Fake] start()"); }

    @Override
    public void stop() { log("[Fake] stop()"); }

    @Override
    public void open(int streamId, String host, int port) {
        log("[Fake] open streamId=" + streamId + " " + host + ":" + port);
        if (listener != null) listener.onConnectAck(streamId, true, "OK");
    }

    @Override
    public void send(int streamId, byte[] data) {
        if (listener != null && data != null && data.length > 0) listener.onData(streamId, data);
        log("[Fake] send streamId=" + streamId + " bytes=" + (data == null ? 0 : data.length));
    }

    @Override
    public void close(int streamId, String reason) {
        log("[Fake] close streamId=" + streamId + " reason=" + reason);
        if (listener != null) listener.onClose(streamId, reason);
    }

    private void log(String s) {
        if (listener != null) listener.onLog(sessionId + " :: " + s);
        else System.out.println(sessionId + " :: " + s);
    }
}
