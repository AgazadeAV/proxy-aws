package org.example.webrtc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.onvoid.webrtc.CreateSessionDescriptionObserver;
import dev.onvoid.webrtc.PeerConnectionFactory;
import dev.onvoid.webrtc.PeerConnectionObserver;
import dev.onvoid.webrtc.RTCAnswerOptions;
import dev.onvoid.webrtc.RTCConfiguration;
import dev.onvoid.webrtc.RTCDataChannel;
import dev.onvoid.webrtc.RTCDataChannelBuffer;
import dev.onvoid.webrtc.RTCDataChannelInit;
import dev.onvoid.webrtc.RTCDataChannelObserver;
import dev.onvoid.webrtc.RTCIceCandidate;
import dev.onvoid.webrtc.RTCIceConnectionState;
import dev.onvoid.webrtc.RTCIceServer;
import dev.onvoid.webrtc.RTCIceTransportPolicy;
import dev.onvoid.webrtc.RTCOfferOptions;
import dev.onvoid.webrtc.RTCPeerConnection;
import dev.onvoid.webrtc.RTCSdpType;
import dev.onvoid.webrtc.RTCSessionDescription;
import dev.onvoid.webrtc.RTCSignalingState;
import dev.onvoid.webrtc.SetSessionDescriptionObserver;
import org.example.common.Frame;
import org.example.common.JsonCodec;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Реальная имплементация Transport на базе webrtc-java 0.13.0.
 * Один DataChannel ("tunnel") для обмена JSON-кадрами Frame.
 * <p>
 * Принципы:
 * - controller/agent оба используют этот класс.
 * - Для controller DataChannel создаётся лениво при первом open()/send().
 * - Для agent DataChannel чаще приходит через onDataChannel (удалённая сторона создала).
 * - Сигналинг файловый/HTTP снаружи через public-методы createOffer/setRemote*.
 */
public class WebRtcTransport implements Transport {

    private final CredsProvider credsProvider;
    private Listener listener;
    private String sessionId = "unknown";

    private volatile boolean started = false;

    private PeerConnectionFactory factory;
    private RTCPeerConnection pc;
    private RTCDataChannel dc;

    private final ObjectMapper om = new ObjectMapper();

    // Накапливаем ICE-кандидаты удалённой стороны (если придут до pc готовности)
    private final List<RTCIceCandidate> pendingRemoteCandidates = new ArrayList<>();

    public WebRtcTransport(CredsProvider credsProvider) {
        this.credsProvider = Objects.requireNonNull(credsProvider, "credsProvider");
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void setSessionId(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) this.sessionId = sessionId;
    }

    @Override
    public synchronized void start() {
        ensureNotStarted();
        // Фабрика и PC
        factory = new PeerConnectionFactory();

        RTCConfiguration cfg = buildRtcConfig();
        pc = factory.createPeerConnection(cfg, new PeerConnectionObserver() {
            @Override
            public void onIceCandidate(RTCIceCandidate candidate) {
                log("ICE candidate: sdpMid=" + candidate.sdpMid +
                        ", mLine=" + candidate.sdpMLineIndex +
                        ", sdp=" + candidate.sdp);
                // Если у вас будет trickle=true, экспортируйте candidate наружу (не реализовано здесь)
            }

            @Override
            public void onIceConnectionChange(RTCIceConnectionState newState) {
                log("ICE " + newState);
            }

            @Override
            public void onSignalingChange(RTCSignalingState newState) {
                log("Signaling " + newState);
            }

            @Override
            public void onDataChannel(RTCDataChannel channel) {
                log("onDataChannel: " + channel.getLabel());
                attachDataChannel(channel);
            }
        });

        started = true;
        log("started");
    }

    @Override
    public synchronized void stop() {
        if (!started) return;
        try {
            if (dc != null) {
                dc.unregisterObserver();
                dc.close();
                dc.dispose();
            }
        } catch (Throwable ignored) {
        }
        try {
            if (pc != null) pc.close();
        } catch (Throwable ignored) {
        }
        try {
            // factory в этой версии без явного dispose; оставим так
        } catch (Throwable ignored) {
        }
        dc = null;
        pc = null;
        factory = null;
        started = false;
        log("stopped");
    }

    @Override
    public void open(int streamId, String host, int port) {
        ensureStarted();
        ensureDataChannel();

        // controller -> agent: запрос CONNECT
        Frame f = Frame.connect(sessionId, streamId, host, port);
        sendFrame(f);
    }

    @Override
    public void send(int streamId, byte[] data) {
        ensureStarted();
        ensureDataChannel();

        Frame f = Frame.data(sessionId, streamId, data);
        sendFrame(f);
    }

    @Override
    public void close(int streamId, String reason) {
        ensureStarted();
        ensureDataChannel();

        Frame f = Frame.close(sessionId, streamId, reason);
        sendFrame(f);
    }

    @Override
    public void ack(int streamId, boolean ok, String reason) {
        ensureStarted();
        ensureDataChannel();
        Frame f = Frame.connectAck(sessionId, streamId, ok, reason);
        sendFrame(f);
    }

    /**
     * Создать SDP offer (trickle=false — будем полагаться на ICE в SDP по умолчанию).
     */
// WebRtcTransport.java
    public String createOffer(boolean trickle) {
        ensureStarted();
        // ВАЖНО: создать канал до offer, чтобы он попал в SDP
        ensureDataChannel();

        CompletableFuture<RTCSessionDescription> fut = new CompletableFuture<>();
        pc.createOffer(new RTCOfferOptions(), new CreateSessionDescriptionObserver() {
            @Override
            public void onSuccess(RTCSessionDescription desc) {
                fut.complete(desc);
            }

            @Override
            public void onFailure(String error) {
                fut.completeExceptionally(new RuntimeException(error));
            }
        });
        RTCSessionDescription offer = join(fut, "createOffer");

        CompletableFuture<Void> setFut = new CompletableFuture<>();
        pc.setLocalDescription(offer, new SetSessionDescriptionObserver() {
            @Override
            public void onSuccess() {
                setFut.complete(null);
            }

            @Override
            public void onFailure(String error) {
                setFut.completeExceptionally(new RuntimeException(error));
            }
        });
        join(setFut, "setLocalDescription(offer)");

        return offer.sdp;
    }

    /**
     * Применить удалённый answer (для controller).
     */
    public void setRemoteAnswer(String sdp) {
        ensureStarted();
        RTCSessionDescription ans = new RTCSessionDescription(RTCSdpType.ANSWER, sdp);
        CompletableFuture<Void> fut = new CompletableFuture<>();
        pc.setRemoteDescription(ans, new SetSessionDescriptionObserver() {
            @Override
            public void onSuccess() {
                fut.complete(null);
            }

            @Override
            public void onFailure(String error) {
                fut.completeExceptionally(new RuntimeException(error));
            }
        });
        join(fut, "setRemoteAnswer");
        // Применим отложенные ICE-кандидаты, если были
        flushPendingCandidates();
    }

    /**
     * Применить удалённый offer (для agent).
     */
    public void setRemoteOffer(String sdp) {
        ensureStarted();
        RTCSessionDescription off = new RTCSessionDescription(RTCSdpType.OFFER, sdp);
        CompletableFuture<Void> fut = new CompletableFuture<>();
        pc.setRemoteDescription(off, new SetSessionDescriptionObserver() {
            @Override
            public void onSuccess() {
                fut.complete(null);
            }

            @Override
            public void onFailure(String error) {
                fut.completeExceptionally(new RuntimeException(error));
            }
        });
        join(fut, "setRemoteOffer");
        // Применим отложенные ICE-кандидаты, если были
        flushPendingCandidates();
    }

    /**
     * Создать SDP answer (для agent).
     */
    public String createAnswer(boolean trickle) {
        ensureStarted();
        CompletableFuture<RTCSessionDescription> fut = new CompletableFuture<>();
        pc.createAnswer(new RTCAnswerOptions(), new CreateSessionDescriptionObserver() {
            @Override
            public void onSuccess(RTCSessionDescription desc) {
                fut.complete(desc);
            }

            @Override
            public void onFailure(String error) {
                fut.completeExceptionally(new RuntimeException(error));
            }
        });

        RTCSessionDescription ans = join(fut, "createAnswer");

        CompletableFuture<Void> setFut = new CompletableFuture<>();
        pc.setLocalDescription(ans, new SetSessionDescriptionObserver() {
            @Override
            public void onSuccess() {
                setFut.complete(null);
            }

            @Override
            public void onFailure(String error) {
                setFut.completeExceptionally(new RuntimeException(error));
            }
        });
        join(setFut, "setLocalDescription(answer)");

        return ans.sdp;
    }

    /**
     * (опционально) принять ICE-кандидат; candidateJson поддерживает {"candidate","sdpMid","sdpMLineIndex"} либо сырой candidate-стринг.
     */
    public void addRemoteIceCandidate(String candidateJson) {
        ensureStarted();
        try {
            RTCIceCandidate cand;
            if (candidateJson.trim().startsWith("{")) {
                JsonNode n = om.readTree(candidateJson);
                String candidate = n.path("candidate").asText(null);
                String sdpMid = n.hasNonNull("sdpMid") ? n.get("sdpMid").asText() : null;
                int sdpMLineIndex = n.hasNonNull("sdpMLineIndex") ? n.get("sdpMLineIndex").asInt() : 0;
                cand = new RTCIceCandidate(sdpMid, sdpMLineIndex, candidate);
            } else {
                // только строка candidate — sdpMid и mLineIndex возьмём по умолчанию
                cand = new RTCIceCandidate("data", 0, candidateJson);
            }

            if (pc == null) {
                synchronized (pendingRemoteCandidates) {
                    pendingRemoteCandidates.add(cand);
                }
                return;
            }
            pc.addIceCandidate(cand);
        } catch (Exception e) {
            log("addRemoteIceCandidate error: " + e.getMessage());
        }
    }

    /* ====================== Внутренние ====================== */

    private RTCConfiguration buildRtcConfig() {
        RTCConfiguration cfg = new RTCConfiguration();
        IceConfig ic = credsProvider.current();

        // policy
        try {
            // relay | all
            String pol = ic.getOptions().getIceTransportPolicy();
            if ("relay".equalsIgnoreCase(pol)) {
                cfg.iceTransportPolicy = RTCIceTransportPolicy.RELAY;
            } else {
                cfg.iceTransportPolicy = RTCIceTransportPolicy.ALL;
            }
        } catch (Exception ignored) {
        }

        // servers
        if (ic.getIceServers() != null) {
            for (IceConfig.IceServer s : ic.getIceServers()) {
                RTCIceServer srv = new RTCIceServer();
                if (s.getUrls() != null) srv.urls.addAll(s.getUrls());
                if (s.getUsername() != null) srv.username = s.getUsername();
                if (s.getCredential() != null) srv.password = s.getCredential();
                cfg.iceServers.add(srv);
            }
        }
        return cfg;
    }

    private void ensureNotStarted() {
        if (started) throw new IllegalStateException("Transport already started");
    }

    private void ensureStarted() {
        if (!started) throw new IllegalStateException("Transport is not started");
    }

    private synchronized void ensureDataChannel() {
        if (dc != null) return;
        if (pc == null) throw new IllegalStateException("PeerConnection is null");

        // создаём один канал с фиксированным label
        RTCDataChannelInit init = new RTCDataChannelInit();
        dc = pc.createDataChannel("tunnel", init);
        attachDataChannel(dc);
    }

    private void attachDataChannel(RTCDataChannel chan) {
        this.dc = chan;
        chan.registerObserver(new RTCDataChannelObserver() {
            @Override
            public void onMessage(RTCDataChannelBuffer buffer) {
                try {
                    // Мы шлём JSON-кадры Frame. Если пришёл бинарь — попробуем как UTF-8, иначе игнор.
                    byte[] bytes;
                    if (buffer.data.hasArray()) {
                        bytes = buffer.data.array();
                    } else {
                        bytes = new byte[buffer.data.remaining()];
                        buffer.data.get(bytes);
                    }

                    Frame f = JsonCodec.decodeBytes(bytes);
                    handleIncomingFrame(f);
                } catch (Exception e) {
                    log("onMessage parse error: " + e.getMessage());
                }
            }

            @Override
            public void onBufferedAmountChange(long previousAmount) { /* no-op */ }

            @Override
            public void onStateChange() {
                log("DataChannel state=" + chan.getState());
            }
        });
    }

    private void handleIncomingFrame(Frame f) {
        if (f == null || listener == null) return;

        switch (f.cmd) {
            case CONNECT_ACK -> listener.onConnectAck(f.streamId, f.ok, f.reason);
            case DATA -> listener.onData(f.streamId, f.payload != null ? f.payload : new byte[0]);
            case CLOSE -> listener.onClose(f.streamId, f.reason);
            case CONNECT -> {
                // Это пришло на агент — открыть локальный TCP и ответить ACK через onConnectAck
                // В вашей архитектуре это делает Agent.StreamRouter.onIncomingConnect(...)
                // Здесь просто пробрасываем событие вверх как лог, а ACK/данные вернутся обычным путём:
                if (listener != null) {
                    listener.onLog(sessionId + " :: CONNECT " + f.host + ":" + f.port + " streamId=" + f.streamId);
                    listener.onIncomingConnect(f.streamId, f.host, f.port); // ← вот это главное
                }
                // Агент должен где-то вызвать onIncomingConnect(...) и после успешного dial отправить CONNECT_ACK:
                // мы можем отправлять ACK прямо отсюда, если у вас будет обратный вызов.
                // Оставляем как есть: бизнес-логика на агенте сама откроет TCP и начнёт слать DATA/ACK через sendFrame().
            }
            default -> {
                // PING/PONG и прочее — опционально
            }
        }
    }

    private void sendFrame(Frame f) {
        try {
            byte[] json = JsonCodec.encodeBytes(f);
            ByteBuffer payload = ByteBuffer.wrap(json);
            RTCDataChannelBuffer buf = new RTCDataChannelBuffer(payload, /*binary=*/true);
            dc.send(buf);
        } catch (Exception e) {
            log("sendFrame error: " + e.getMessage());
        }
    }

    private void flushPendingCandidates() {
        if (pendingRemoteCandidates.isEmpty() || pc == null) return;
        synchronized (pendingRemoteCandidates) {
            for (RTCIceCandidate c : pendingRemoteCandidates) {
                try {
                    pc.addIceCandidate(c);
                } catch (Exception ignored) {
                }
            }
            pendingRemoteCandidates.clear();
        }
    }

    private <T> T join(CompletableFuture<T> fut, String where) {
        try {
            return fut.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(where + " failed: " + e.getMessage(), e);
        }
    }

    private void log(String s) {
        if (listener != null) listener.onLog(sessionId + " :: " + s);
        else System.out.println(sessionId + " :: " + s);
    }
}
