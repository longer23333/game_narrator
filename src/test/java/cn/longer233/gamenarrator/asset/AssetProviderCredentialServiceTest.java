package cn.longer233.gamenarrator.asset;

import cn.longer233.gamenarrator.identity.LocalSecretCipher;
import cn.longer233.gamenarrator.identity.LocalUserContext;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AssetProviderCredentialServiceTest {
    @TempDir Path temporaryDirectory;

    @Test
    void encryptsAccountKeyIsolatesUsersAndFallsBackToEnvironment() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:asset-provider-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        LocalUserContext user = new LocalUserContext();
        user.begin(LocalUserContext.LOCAL_USER_ID, "USER", true);
        LocalSecretCipher cipher = new LocalSecretCipher(temporaryDirectory.toString(), "");
        AssetProviderCredentialService service = new AssetProviderCredentialService(
                jdbc, user, cipher, "deployment-pexels-key", "");

        assertThat(service.status("PEXELS").source()).isEqualTo("ENVIRONMENT");
        service.save(new AssetProviderCredentialRequest("PEXELS", "account-secret-1234"));

        String stored = jdbc.queryForObject("SELECT api_key_ciphertext FROM user_asset_provider_config WHERE user_id=?",
                String.class, LocalUserContext.LOCAL_USER_ID);
        assertThat(stored).startsWith("v1:").doesNotContain("account-secret-1234");
        assertThat(service.effectiveKey("PEXELS")).isEqualTo("account-secret-1234");
        assertThat(service.status("PEXELS").source()).isEqualTo("ACCOUNT");
        assertThat(service.status("PEXELS").keyHint()).isEqualTo("****1234");

        UUID anotherUser = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_user(id,username,display_name,role,status,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?)
                """, anotherUser, "another-user", "Another", "USER", "ACTIVE",
                OffsetDateTime.now(), OffsetDateTime.now());
        user.begin(anotherUser, "USER", true);
        assertThat(service.effectiveKey("PEXELS")).isEqualTo("deployment-pexels-key");

        user.begin(LocalUserContext.LOCAL_USER_ID, "USER", true);
        assertThat(service.delete("PEXELS").source()).isEqualTo("ENVIRONMENT");
        assertThat(service.effectiveKey("PEXELS")).isEqualTo("deployment-pexels-key");
    }
}
