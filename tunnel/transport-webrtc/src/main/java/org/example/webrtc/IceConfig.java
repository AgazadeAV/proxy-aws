package org.example.webrtc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * ICE/TURN конфиг + общие опции транспорта.
 * Ожидает два файла в каталоге конфигурации:
 * - ice.json        : массив IceServer (urls, username, credential)
 * - transport.json  : TransportOptions
 */
public final class IceConfig {

    /**
     * Один STUN/TURN сервер.
     */
    public static class IceServer {
        private List<String> urls;
        private String username;
        private String credential;

        public IceServer() {
        }

        public IceServer(List<String> urls, String username, String credential) {
            this.urls = urls;
            this.username = username;
            this.credential = credential;
        }

        public List<String> getUrls() {
            return urls;
        }

        public void setUrls(List<String> urls) {
            this.urls = urls;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getCredential() {
            return credential;
        }

        public void setCredential(String credential) {
            this.credential = credential;
        }
    }

    /**
     * Опции транспорта.
     */
    public static final class TransportOptions {
        private String iceTransportPolicy = "relay"; // relay|all
        private boolean preferTcp443 = true;
        private int chunkSize = 16 * 1024;           // bytes
        private int mtu = 1400;                      // bytes (на будущее)
        private int connectTimeoutMs = 15_000;
        private int reconnectBackoffMs = 2_000;

        public TransportOptions() {
        }

        public String getIceTransportPolicy() {
            return iceTransportPolicy;
        }

        public void setIceTransportPolicy(String iceTransportPolicy) {
            this.iceTransportPolicy = iceTransportPolicy;
        }

        public boolean isPreferTcp443() {
            return preferTcp443;
        }

        public void setPreferTcp443(boolean preferTcp443) {
            this.preferTcp443 = preferTcp443;
        }

        public int getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }

        public int getMtu() {
            return mtu;
        }

        public void setMtu(int mtu) {
            this.mtu = mtu;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReconnectBackoffMs() {
            return reconnectBackoffMs;
        }

        public void setReconnectBackoffMs(int reconnectBackoffMs) {
            this.reconnectBackoffMs = reconnectBackoffMs;
        }
    }

    private final List<IceServer> iceServers;
    private final TransportOptions options;

    public IceConfig(List<IceServer> iceServers, TransportOptions options) {
        this.iceServers = iceServers == null ? Collections.emptyList() : iceServers;
        this.options = options == null ? new TransportOptions() : options;
    }

    public List<IceServer> getIceServers() {
        return iceServers;
    }

    public TransportOptions getOptions() {
        return options;
    }

    /**
     * Загрузка из директории (ice.json, transport.json).
     */
    public static IceConfig load(Path configDir) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            Path iceFile = configDir.resolve("ice.json");
            Path transportFile = configDir.resolve("transport.json");

            List<IceServer> servers = Files.exists(iceFile)
                    ? mapper.readValue(Files.readAllBytes(iceFile), new TypeReference<>() {
            })
                    : Collections.emptyList();

            TransportOptions opts = Files.exists(transportFile)
                    ? mapper.readValue(Files.readAllBytes(transportFile), TransportOptions.class)
                    : new TransportOptions();

            return new IceConfig(servers, opts);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load IceConfig from " + configDir, e);
        }
    }
}
