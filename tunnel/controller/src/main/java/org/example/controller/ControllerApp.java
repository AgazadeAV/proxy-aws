package org.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.SignalingFiles;
import org.example.webrtc.CredsProvider;
import org.example.webrtc.FakeTransport;
import org.example.webrtc.Transport;
// import org.example.webrtc.WebRtcTransport; // позже

import java.nio.file.Path;
import java.util.Arrays;

public class ControllerApp {
    public static void main(String[] args) throws Exception {
        Path cfgDir = Path.of(System.getProperty("configs.dir", "configs"));

        ObjectMapper om = new ObjectMapper();
        ControllerConfig cfg = om.readValue(cfgDir.resolve("app.controller.json").toFile(), ControllerConfig.class);

        CredsProvider creds = CredsProvider.fromDir(cfgDir);

        // MVP: FakeTransport. Позже заменим на WebRtcTransport(creds)
        Transport transport = new FakeTransport();
        // Transport transport = new WebRtcTransport(creds);

        StreamRouter router = new StreamRouter(cfg.getSessionId(), transport);
        transport.setListener(router);
        transport.setSessionId(cfg.getSessionId());
        transport.start();

        // --- Ручной сигналинг (один из режимов, потом завершаем или продолжаем работу) ---
        // Аргументы:
        //   --sig-offer-out=offer.sdp        (создать offer и записать)
        //   --sig-answer-in=answer.sdp       (прочитать answer и применить)
        boolean didSignalAction = false;
        String offerOut = argValue(args, "--sig-offer-out");
        String answerIn = argValue(args, "--sig-answer-in");
        boolean trickle = false; // рекомендуем trickle=false: все ICE в SDP

        if (offerOut != null) {
            // var wrt = (WebRtcTransport) transport;
            // String sdp = wrt.createOffer(trickle);
            String sdp = "OFFER_PLACEHOLDER"; // удалить после JNI
            SignalingFiles.writeText(Path.of(offerOut), sdp);
            didSignalAction = true;
        }

        if (answerIn != null) {
            String sdp = SignalingFiles.readText(Path.of(answerIn));
            // ((WebRtcTransport) transport).setRemoteAnswer(sdp);
            didSignalAction = true;
        }

        if (didSignalAction) {
            // если запускали только ради сигналинга — можно завершить/или продолжить SOCKS
            System.out.println("[Controller] signaling step done");
        }

        Socks5Server socks = new Socks5Server("127.0.0.1", cfg.getSocksPort(), router);
        socks.start();

        System.out.println("[Controller] SOCKS5 on 127.0.0.1:" + cfg.getSocksPort());
        System.out.println("[Controller] Press Ctrl+C to exit");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { socks.stop(); } catch (Exception ignored) {}
            try { transport.stop(); } catch (Exception ignored) {}
        }));

        Thread.currentThread().join();
    }

    private static String argValue(String[] args, String key) {
        return Arrays.stream(args)
                .filter(a -> a.startsWith(key + "="))
                .map(a -> a.substring((key + "=").length()))
                .findFirst().orElse(null);
    }
}
