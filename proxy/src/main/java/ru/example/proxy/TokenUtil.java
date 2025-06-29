package ru.example.proxy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class TokenUtil {

    public static String generateToken(String sessionId) {
        return Base64.getEncoder().encodeToString(sessionId.getBytes(StandardCharsets.UTF_8));
    }
}
