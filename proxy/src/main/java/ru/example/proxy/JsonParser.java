package ru.example.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonParser {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static String extractPayload(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            return node.get("payload").asText();
        } catch (Exception e) {
            System.err.println("Failed to parse payload: " + e.getMessage());
            return null;
        }
    }
}
