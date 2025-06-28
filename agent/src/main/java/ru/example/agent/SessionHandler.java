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
        System.out.println(token);
        String target = (String) task.get("target");
        String payloadBase64 = (String) task.get("payload");

        // Валидация токена
        String parsedSessionId = SessionTokenUtil.parseSessionId(token);
        System.out.println(parsedSessionId);
        System.out.println(sessionId);


        if (!parsedSessionId.equals(sessionId)) {
            throw new SecurityException("Invalid token for session");
        }

        // Установить соединение
        AgentSessionManager.openSession(sessionId, target);

        // Отправка данных
        byte[] payload = Base64.getDecoder().decode(payloadBase64);
        AgentSessionManager.sendData(sessionId, payload);

        // Получение ответа
        byte[] response = AgentSessionManager.receiveData(sessionId);
        String encoded = Base64.getEncoder().encodeToString(response);

        // Возврат JSON
        return "{\"sessionId\":\"" + sessionId + "\",\"payload\":\"" + encoded + "\"}";
    }
}
