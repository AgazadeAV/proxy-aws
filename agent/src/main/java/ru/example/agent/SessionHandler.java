package ru.example.agent;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.Map;

public class SessionHandler {
    private static final ObjectMapper mapper = new ObjectMapper();

    public String handle(String jsonTask) throws Exception {
        Map<String, Object> task = mapper.readValue(jsonTask, Map.class);

        String sessionId = (String) task.get("sessionId");
        String token = (String) task.get("token");
        String payloadBase64 = (String) task.get("payload");

        String parsedSessionId = SessionTokenUtil.parseSessionId(token);

        if (!parsedSessionId.equals(sessionId)) {
            throw new SecurityException("Invalid token for session");
        }

        if (!AgentSessionManager.hasSession(sessionId)) {
            String target = (String) task.get("target");
            if (target == null || target.isBlank()) {
                throw new IllegalArgumentException("Missing target for new session");
            }
            AgentSessionManager.openSession(sessionId, target);
        }

        byte[] payload = Base64.getDecoder().decode(payloadBase64);
        AgentSessionManager.sendData(sessionId, payload);

        byte[] response = AgentSessionManager.receiveData(sessionId);
        String encoded = Base64.getEncoder().encodeToString(response);

        return "{\"sessionId\":\"" + sessionId + "\",\"payload\":\"" + encoded + "\"}";
    }
}
