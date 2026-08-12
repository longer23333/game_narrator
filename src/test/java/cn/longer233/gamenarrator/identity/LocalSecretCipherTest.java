package cn.longer233.gamenarrator.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalSecretCipherTest {
    @TempDir Path root;

    @Test
    void refusesToSilentlyReplaceADeletedMasterKey() throws Exception {
        LocalSecretCipher cipher = new LocalSecretCipher(root.toString(), "");
        String encrypted = cipher.encrypt("credential");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("credential");
        Files.delete(root.resolve("config/local-master.key"));

        assertThatThrownBy(() -> new LocalSecretCipher(root.toString(), ""))
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("Local master key is missing; restore local-master.key from backup");
    }

    @Test
    void rotatesLegacyCiphertextToTheActiveKeyVersion() {
        String oldKey = key();
        String newKey = key();
        LocalSecretCipher legacy = new LocalSecretCipher(root.toString(), oldKey, "v1", "");
        String encrypted = legacy.encrypt("credential");
        LocalSecretCipher rotated = new LocalSecretCipher(root.toString(), newKey, "v2", "v1=" + oldKey);

        assertThat(rotated.decrypt(encrypted)).isEqualTo("credential");
        assertThat(rotated.needsRotation(encrypted)).isTrue();
        String replacement = rotated.rotate(encrypted);
        assertThat(replacement).startsWith("v2:");
        assertThat(rotated.decrypt(replacement)).isEqualTo("credential");
        assertThat(rotated.needsRotation(replacement)).isFalse();
    }

    @Test
    void refusesCiphertextWhenItsKeyVersionIsUnavailable() {
        LocalSecretCipher v1 = new LocalSecretCipher(root.toString(), key(), "v1", "");
        String encrypted = v1.encrypt("credential");
        LocalSecretCipher v2 = new LocalSecretCipher(root.toString(), key(), "v2", "");

        assertThatThrownBy(() -> v2.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("Master key version is unavailable: v1");
    }

    private static String key() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
