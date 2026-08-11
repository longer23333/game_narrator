package cn.longer233.gamenarrator.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

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
                .hasRootCauseMessage("本地主密钥已丢失，请从备份恢复 local-master.key，或重新配置所有加密凭据");
    }
}
