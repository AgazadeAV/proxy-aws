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

        if (!SessionTokenUtil.parseSessionId(token).equals(sessionId)) {
            throw new SecurityException("Invalid token for session");
        }

        byte[] payload = Base64.getDecoder().decode(payloadBase64);

        byte[] response = AgentLogic.process(sessionId, payload);

        String encoded = Base64.getEncoder().encodeToString(response);
        return "{\"sessionId\":\"" + sessionId + "\",\"payload\":\"" + encoded + "\"}";
    }
}
