package cn.longer233.gamenarrator.identity;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecretRotationServiceTest {
    @Test
    void rotatesEveryCredentialTableAndPreservesPlaintext() {
        JdbcTemplate jdbc = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:h2:mem:key-rotation;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE user_cloud_ai_config(user_id UUID PRIMARY KEY,api_key_ciphertext CLOB)");
        jdbc.execute("CREATE TABLE user_asset_provider_config(user_id UUID,provider VARCHAR(32),api_key_ciphertext CLOB,PRIMARY KEY(user_id,provider))");
        String oldKey = key(), newKey = key();
        LocalSecretCipher oldCipher = new LocalSecretCipher(".", oldKey, "v1", "");
        LocalSecretCipher cipher = new LocalSecretCipher(".", newKey, "v2", "v1=" + oldKey);
        UUID user = UUID.randomUUID();
        jdbc.update("INSERT INTO user_cloud_ai_config VALUES(?,?)", user, oldCipher.encrypt("cloud-key"));
        jdbc.update("INSERT INTO user_asset_provider_config VALUES(?,?,?)", user, "PEXELS", oldCipher.encrypt("asset-key"));
        CurrentUserContext current = mock(CurrentUserContext.class);
        when(current.role()).thenReturn("ADMIN");

        var result = new SecretRotationService(jdbc, cipher, current).rotateAll();

        assertThat(result.rotated()).isEqualTo(2);
        assertThat(result.activeVersion()).isEqualTo("v2");
        assertThat(cipher.decrypt(jdbc.queryForObject("SELECT api_key_ciphertext FROM user_cloud_ai_config", String.class))).isEqualTo("cloud-key");
        assertThat(cipher.decrypt(jdbc.queryForObject("SELECT api_key_ciphertext FROM user_asset_provider_config", String.class))).isEqualTo("asset-key");
    }

    @Test
    void rejectsNonAdministrators() {
        CurrentUserContext current = mock(CurrentUserContext.class);
        when(current.role()).thenReturn("USER");
        assertThatThrownBy(() -> new SecretRotationService(mock(JdbcTemplate.class),
                new LocalSecretCipher(".", key(), "v2", ""), current).rotateAll())
                .isInstanceOf(cn.longer233.gamenarrator.admin.AdminAccessDeniedException.class);
    }

    private static String key() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
