package org.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.SignalingFiles;
import org.example.webrtc.CredsProvider;
import org.example.webrtc.WebRtcTransport;

import java.nio.file.Path;
import java.util.Arrays;

public class ControllerApp {
    public static void main(String[] args) throws Exception {
        Path cfgDir = Path.of(System.getProperty("configs.dir", "configs"));

        ObjectMapper om = new ObjectMapper();
        ControllerConfig cfg = om.readValue(cfgDir.resolve("app.controller.json").toFile(), ControllerConfig.class);

        CredsProvider creds = CredsProvider.fromDir(cfgDir);
        WebRtcTransport transport = new WebRtcTransport(creds);

        StreamRouter router = new StreamRouter(cfg.getSessionId(), transport);
        transport.setListener(router);
        transport.setSessionId(cfg.getSessionId());
        transport.start();

        // --- Ручной сигналинг (trickle=false) ---
        boolean didSignalAction = false;
        String offerOut = argValue(args, "--sig-offer-out");
        String answerIn = argValue(args, "--sig-answer-in");
        boolean trickle = false; // все ICE в SDP

        if (offerOut != null) {
            String sdp = transport.createOffer(trickle);
            SignalingFiles.writeText(Path.of(offerOut), sdp);
            didSignalAction = true;
        }

        if (answerIn != null) {
            String sdp = SignalingFiles.readText(Path.of(answerIn));
            transport.setRemoteAnswer(sdp);
            didSignalAction = true;
        }

        if (didSignalAction) {
            System.out.println("[Controller] signaling step done");
        }

        // --- SOCKS5 proxy ---
        Socks5Server socks = new Socks5Server("127.0.0.1", cfg.getSocksPort(), router);
        socks.start();
        System.out.println("[Controller] SOCKS5 on 127.0.0.1:" + cfg.getSocksPort());
        System.out.println("[Controller] Press Ctrl+C to exit");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                socks.stop();
            } catch (Exception ignored) {
            }
            try {
                transport.stop();
            } catch (Exception ignored) {
            }
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
