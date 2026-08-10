package cn.longer233.gamenarrator.identity;

import org.springframework.stereotype.Component;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class PasswordHasher {
    private static final int ITERATIONS = 210_000;
    public String hash(String password) {
        try {
            byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
            byte[] value = derive(password, salt, ITERATIONS);
            return "pbkdf2-sha256$" + ITERATIONS + "$" + Base64.getUrlEncoder().withoutPadding().encodeToString(salt)
                    + "$" + Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        } catch (Exception exception) { throw new IllegalStateException("无法保护密码", exception); }
    }
    public boolean verify(String password, String encoded) {
        try {
            String[] parts = encoded.split("\\$");
            byte[] salt = Base64.getUrlDecoder().decode(parts[2]);
            byte[] expected = Base64.getUrlDecoder().decode(parts[3]);
            return MessageDigest.isEqual(expected, derive(password, salt, Integer.parseInt(parts[1])));
        } catch (Exception ignored) { return false; }
    }
    private byte[] derive(String password, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    }
}
