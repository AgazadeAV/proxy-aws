package ru.example.proxy;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class TokenUtil {
    private static final String SECRET = "1234567890123456"; // 16 байт, AES ключ

    public static String generateToken(String sessionId) {
        try {
            String data = sessionId + ":" + System.currentTimeMillis();
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(SECRET.getBytes(), "AES"));
            byte[] encrypted = cipher.doFinal(data.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate token", e);
        }
    }
}
