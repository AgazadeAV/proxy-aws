package org.example.webrtc;

import java.nio.file.Path;

/**
 * Источник ICE/TURN конфигурации.
 * MVP: грузим из директории (ice.json + transport.json) один раз.
 * Позже можно подвесить автообновление из Teams/Graph.
 */
public interface CredsProvider {
    IceConfig current();

    static CredsProvider fromDir(Path configDir) {
        IceConfig cfg = IceConfig.load(configDir);
        return () -> cfg;
    }
}
