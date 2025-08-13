package org.example.webrtc;

import java.util.Objects;

/**
 * Скелет реального WebRTC-транспорта.
 * Публичный API финальный; внутренняя реализация будет добавлена на шаге JNI:
 *  - создание PeerConnection,
 *  - relay-only TURN (TCP/443 приоритет),
 *  - DataChannel (binary),
 *  - backpressure/keepalive/reconnect.
 *
 * Сейчас методы кидают UnsupportedOperationException, чтобы явно показать,
 * что сюда ещё не подвезена нативная реализация.
 */
public class WebRtcTransport implements Transport {

    private final CredsProvider credsProvider;
    private Listener listener;
    private String sessionId = "unknown";
    private volatile boolean started = false;

    public WebRtcTransport(CredsProvider credsProvider) {
        this.credsProvider = Objects.requireNonNull(credsProvider, "credsProvider");
    }

    @Override
    public void setListener(Listener listener) { this.listener = listener; }

    @Override
    public void setSessionId(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) this.sessionId = sessionId;
    }

    @Override
    public void start() {
        started = true;
        throw new UnsupportedOperationException("WebRTC (JNI) not implemented yet");
    }

    @Override
    public void stop() {
        started = false;
        throw new UnsupportedOperationException("WebRTC (JNI) not implemented yet");
    }

    @Override
    public void open(int streamId, String host, int port) {
        ensureStarted();
        throw new UnsupportedOperationException("WebRTC (JNI) not implemented yet");
    }

    @Override
    public void send(int streamId, byte[] data) {
        ensureStarted();
        throw new UnsupportedOperationException("WebRTC (JNI) not implemented yet");
    }

    @Override
    public void close(int streamId, String reason) {
        ensureStarted();
        throw new UnsupportedOperationException("WebRTC (JNI) not implemented yet");
    }

    private void ensureStarted() {
        if (!started) {
            throw new IllegalStateException("Transport is not started");
        }
    }

    /** Создать SDP offer (trickle=false — кандидаты в SDP) */
    public String createOffer(boolean trickle) {
        ensureStarted();
        throw new UnsupportedOperationException("WebRTC (JNI) not implemented yet");
    }

    /** Применить удалённый answer (SDP) */
    public void setRemoteAnswer(String sdp) {
        ensureStarted();
        throw new UnsupportedOperationException("WebRTC (JNI) not implemented yet");
    }

    /** Применить удалённый offer (SDP) — для Agent */
    public void setRemoteOffer(String sdp) {
        ensureStarted();
        throw new UnsupportedOperationException("WebRTC (JNI) not implemented yet");
    }

    /** Создать SDP answer (trickle=false — кандидаты в SDP) */
    public String createAnswer(boolean trickle) {
        ensureStarted();
        throw new UnsupportedOperationException("WebRTC (JNI) not implemented yet");
    }

    /** (опционально) принять ICE-кандидат, если включим trickle=true */
    public void addRemoteIceCandidate(String candidateJson) {
        ensureStarted();
        throw new UnsupportedOperationException("WebRTC (JNI) not implemented yet");
    }

    @SuppressWarnings("unused")
    private void log(String s) {
        if (listener != null) listener.onLog(sessionId + " :: " + s);
        else System.out.println(sessionId + " :: " + s);
    }
}
