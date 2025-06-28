package ru.example.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import ru.example.shared.SessionTokenUtil;

import java.util.Base64;
import java.util.Map;

public class SessionHandler {
    private static final ObjectMapper mapper = new ObjectMapper();

    public String handle(String jsonRequest) throws Exception {
        System.out.println("[Handler] Incoming task: " + jsonRequest);

        Map<String, Object> req = mapper.readValue(jsonRequest, Map.class);

        String sessionId = (String) req.get("sessionId");
        String token = (String) req.get("token");
        String target = (String) req.get("target");
        String payloadBase64 = (String) req.get("payload");

        String parsedSessionId = SessionTokenUtil.parseSessionId(token);
        System.out.println("[Handler] Token parsed: " + parsedSessionId);

        if (!parsedSessionId.equals(sessionId)) {
            throw new SecurityException("Invalid token for session");
        }

        AgentSessionManager.openSession(sessionId, target);
        System.out.println("[Handler] Connected to: " + target);

        byte[] payload = Base64.getDecoder().decode(payloadBase64);
        AgentSessionManager.sendData(sessionId, payload);
        System.out.println("[Handler] Sent data: " + payload.length + " bytes");

        byte[] received = AgentSessionManager.receiveData(sessionId);
        System.out.println("[Handler] Received data: " + received.length + " bytes");

        String encoded = Base64.getEncoder().encodeToString(received);
        return "{\"payload\":\"" + encoded + "\"}";
    }
}
