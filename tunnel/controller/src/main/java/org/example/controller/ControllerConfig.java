package org.example.controller;

public class ControllerConfig {
    private int socksPort = 1080;
    private String sessionId = "operator-01";

    public ControllerConfig() {}

    public int getSocksPort() { return socksPort; }
    public void setSocksPort(int socksPort) { this.socksPort = socksPort; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
