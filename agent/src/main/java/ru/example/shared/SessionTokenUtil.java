package ru.example.shared;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class SessionTokenUtil {
    private static final String SECRET = "1234567890123456";
    private static final String ALGO = "AES";

    public static String parseSessionId(String token) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(SECRET.getBytes(), ALGO));
        String decrypted = new String(cipher.doFinal(Base64.getDecoder().decode(token)));
        String[] parts = decrypted.split(":");

        long ts = Long.parseLong(parts[1]);
        if (System.currentTimeMillis() - ts > 30_000) throw new RuntimeException("Token expired");

        System.out.println("[TokenUtil] Decrypted sessionId: " + parts[0]);
        return parts[0];
    }
}
