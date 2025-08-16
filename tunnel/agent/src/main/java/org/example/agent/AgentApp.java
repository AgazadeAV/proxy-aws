package org.example.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.SignalingFiles;
import org.example.webrtc.CredsProvider;
import org.example.webrtc.WebRtcTransport;

import java.nio.file.Path;
import java.util.Arrays;

public class AgentApp {
    public static void main(String[] args) throws Exception {
        Path cfgDir = Path.of(System.getProperty("configs.dir", "configs"));

        ObjectMapper om = new ObjectMapper();
        AgentConfig cfg = om.readValue(cfgDir.resolve("app.agent.json").toFile(), AgentConfig.class);

        // --- WebRTC transport ---
        CredsProvider creds = CredsProvider.fromDir(cfgDir);
        WebRtcTransport transport = new WebRtcTransport(creds);

        StreamRouter router = new StreamRouter(cfg.getSessionId(), transport, new TcpDialer(cfg.getConnectTimeoutMs()));
        transport.setListener(router);
        transport.setSessionId(cfg.getSessionId());
        transport.start();

        // --- Ручной сигналинг ---
        //   --sig-offer-in=offer.sdp     (прочитать offer от controller и применить)
        //   --sig-answer-out=answer.sdp  (создать answer и записать)
        boolean didSignalAction = false;
        String offerIn = argValue(args, "--sig-offer-in");
        String answerOut = argValue(args, "--sig-answer-out");
        boolean trickle = false; // можно потом включить

        if (offerIn != null) {
            String sdpOffer = SignalingFiles.readText(Path.of(offerIn));
            transport.setRemoteOffer(sdpOffer);
            didSignalAction = true;
        }

        if (answerOut != null) {
            String sdpAnswer = transport.createAnswer(trickle);
            SignalingFiles.writeText(Path.of(answerOut), sdpAnswer);
            didSignalAction = true;
        }

        if (didSignalAction) {
            System.out.println("[Agent] signaling step done");
        }

        System.out.println("[Agent] started. Press Ctrl+C to exit");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                transport.stop();
            } catch (Exception ignored) {
            }
            router.shutdown();
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
