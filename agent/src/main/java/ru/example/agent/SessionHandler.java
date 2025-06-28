package ru.example.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import ru.example.shared.SessionTokenUtil;

import java.util.Base64;
import java.util.Map;

public class SessionHandler {
    private static final ObjectMapper mapper = new ObjectMapper();

    public String handle(String jsonRequest) throws Exception {
        Map<String, Object> req = mapper.readValue(jsonRequest, Map.class);

        String sessionId = (String) req.get("sessionId");
        String token = (String) req.get("token");
        String target = (String) req.get("target");
        String payloadBase64 = (String) req.get("payload");

        // Валидация токена
        String parsedSessionId = SessionTokenUtil.parseSessionId(token);
        if (!parsedSessionId.equals(sessionId)) {
            throw new SecurityException("Invalid token for session");
        }

        // Выполнение запроса
        AgentSessionManager.openSession(sessionId, target);
        AgentSessionManager.sendData(sessionId, Base64.getDecoder().decode(payloadBase64));
        byte[] received = AgentSessionManager.receiveData(sessionId);
        String encoded = Base64.getEncoder().encodeToString(received);

        return "{\"payload\":\"" + encoded + "\"}";
    }
}
