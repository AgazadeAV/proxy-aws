package ru.example.shared;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class SessionTokenUtil {
    private static final String SECRET = "1234567890123456"; // 16-byte key
    private static final String ALGO = "AES";

    public static String generateToken(String sessionId) throws Exception {
        String data = sessionId + ":" + System.currentTimeMillis();
        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(SECRET.getBytes(), ALGO));
        return Base64.getEncoder().encodeToString(cipher.doFinal(data.getBytes()));
    }
}
