package cn.longer233.gamenarrator.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** AES-GCM key ring with explicit ciphertext key versions and legacy v1 compatibility. */
@Component
public class LocalSecretCipher {
    private static final String LEGACY_VERSION = "v1";
    private final Map<String, SecretKeySpec> keys;
    private final String activeVersion;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public LocalSecretCipher(
            @Value("${game-narrator.data-root:${GAME_NARRATOR_DATA_ROOT:./data}}") String root,
            @Value("${game-narrator.security.master-key:${GAME_NARRATOR_SECRET_KEY:}}") String activeKey,
            @Value("${game-narrator.security.master-key-version:${GAME_NARRATOR_SECRET_KEY_VERSION:v1}}") String version,
            @Value("${game-narrator.security.previous-master-keys:${GAME_NARRATOR_PREVIOUS_SECRET_KEYS:}}") String previousKeys) {
        try {
            this.activeVersion = normalizeVersion(version);
            this.keys = new LinkedHashMap<>();
            byte[] activeBytes = activeKey == null || activeKey.isBlank()
                    ? localKey(root) : decodeKey(activeKey);
            keys.put(activeVersion, new SecretKeySpec(activeBytes, "AES"));
            parsePrevious(previousKeys).forEach((keyVersion, encoded) -> {
                if (keys.containsKey(keyVersion)) throw new IllegalArgumentException("Duplicate master key version: " + keyVersion);
                keys.put(keyVersion, new SecretKeySpec(decodeKey(encoded), "AES"));
            });
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to initialize the credential key ring", exception);
        }
    }

    /** Backward-compatible constructor for focused tests and embedded callers. */
    public LocalSecretCipher(String root, String activeKey) {
        this(root, activeKey, LEGACY_VERSION, "");
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isBlank()) return null;
        return encryptWith(activeVersion, keys.get(activeVersion), plain);
    }

    public String decrypt(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            int separator = value.indexOf(':');
            if (separator <= 0) throw new IllegalArgumentException("Ciphertext has no key version");
            String version = value.substring(0, separator);
            SecretKeySpec key = keys.get(version);
            if (key == null) throw new IllegalStateException("Master key version is unavailable: " + version);
            byte[] all = Base64.getDecoder().decode(value.substring(separator + 1));
            if (all.length <= 12) throw new IllegalArgumentException("Ciphertext is truncated");
            byte[] iv = Arrays.copyOfRange(all, 0, 12);
            byte[] data = Arrays.copyOfRange(all, 12, all.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to decrypt a stored credential", exception);
        }
    }

    public boolean needsRotation(String value) {
        return value != null && !value.isBlank() && !value.startsWith(activeVersion + ":");
    }

    public String rotate(String value) {
        return value == null || value.isBlank() || !needsRotation(value) ? value : encrypt(decrypt(value));
    }

    public String activeVersion() { return activeVersion; }

    private String encryptWith(String version, SecretKeySpec key, String plain) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] all = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(encrypted, 0, all, iv.length, encrypted.length);
            return version + ":" + Base64.getEncoder().encodeToString(all);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encrypt a credential", exception);
        }
    }

    private static byte[] localKey(String root) throws Exception {
        Path file = Path.of(root).toAbsolutePath().normalize().resolve("config/local-master.key");
        Path marker = file.resolveSibling("local-master.key.initialized");
        if (Files.isRegularFile(file)) {
            byte[] bytes = decodeKey(Files.readString(file));
            if (!Files.exists(marker)) Files.writeString(marker, LEGACY_VERSION, StandardCharsets.UTF_8);
            return bytes;
        }
        if (Files.exists(marker)) {
            throw new IllegalStateException("Local master key is missing; restore local-master.key from backup");
        }
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        Files.createDirectories(file.getParent());
        Files.writeString(file, Base64.getEncoder().encodeToString(bytes), StandardCharsets.UTF_8);
        Files.writeString(marker, LEGACY_VERSION, StandardCharsets.UTF_8);
        return bytes;
    }

    private static byte[] decodeKey(String encoded) {
        byte[] bytes = Base64.getDecoder().decode(encoded.strip());
        if (bytes.length != 32) throw new IllegalArgumentException("Master keys must be 32-byte Base64 values");
        return bytes;
    }

    private static String normalizeVersion(String value) {
        String normalized = value == null ? "" : value.strip();
        if (!normalized.matches("v[1-9][0-9]*")) throw new IllegalArgumentException("Master key version must match v<number>");
        return normalized;
    }

    private static Map<String, String> parsePrevious(String value) {
        Map<String, String> result = new LinkedHashMap<>();
        if (value == null || value.isBlank()) return result;
        for (String entry : value.split(",")) {
            String[] pair = entry.strip().split("=", 2);
            if (pair.length != 2) throw new IllegalArgumentException("Previous keys must use version=base64 entries");
            result.put(normalizeVersion(pair[0]), pair[1].strip());
        }
        return result;
    }
}
