package ru.example.agent;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class SessionTokenUtil {
    private static final String SECRET = "1234567890123456"; // 16 байт для AES-128
    private static final String ALGO = "AES";

    public static String parseSessionId(String token) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(SECRET.getBytes(), ALGO));
        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(token));
        String decrypted = new String(decryptedBytes);

        String[] parts = decrypted.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid token format");
        }

        return parts[0];
    }
}
