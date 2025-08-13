package org.example.agent;

public class AgentConfig {
    private String sessionId = "corp-agent-01";
    private int connectTimeoutMs = 15000;

    public AgentConfig() {
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }
}
