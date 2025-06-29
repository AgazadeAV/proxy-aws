package ru.example.agent;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class TokenUtil {
    public static String extractSessionId(String token) {
        return new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8);
    }
}
