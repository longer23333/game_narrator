package cn.longer233.gamenarrator;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionCredentialGuardTest {
    private static final String VALID_SECRET = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void acceptsExplicitPostgresqlCredentialsWithoutCloudSync() {
        assertThatCode(() -> validate("database-password", VALID_SECRET, true, false, null, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingDatabasePasswordApplicationSecretAndMandatoryLogin() {
        assertRejected("POSTGRES_PASSWORD", null, VALID_SECRET, true, false, null, null, null);
        assertRejected("GAME_NARRATOR_SECRET_KEY", "database-password", null, true, false, null, null, null);
        assertRejected("REQUIRE_LOGIN=true", "database-password", VALID_SECRET, false, false, null, null, null);
    }

    @Test
    void rejectsMalformedApplicationSecret() {
        assertRejected("32 字节", "database-password", "not-base64", true, false, null, null, null);
        assertRejected("32 字节", "database-password",
                Base64.getEncoder().encodeToString(new byte[16]), true, false, null, null, null);
    }

    @Test
    void requiresCompleteObjectStorageCredentialsOnlyWhenCloudSyncIsEnabled() {
        assertRejected("OBJECT_STORAGE_ENDPOINT", "database-password", VALID_SECRET, true, true,
                null, "access", "secret");
        assertRejected("OBJECT_STORAGE_ACCESS_KEY", "database-password", VALID_SECRET, true, true,
                "https://storage.example.com", null, "secret");
        assertRejected("OBJECT_STORAGE_SECRET_KEY", "database-password", VALID_SECRET, true, true,
                "https://storage.example.com", "access", null);
        assertThatCode(() -> validate("database-password", VALID_SECRET, true, true,
                "https://storage.example.com", "access", "secret")).doesNotThrowAnyException();
    }

    private static void assertRejected(String message, String databasePassword, String applicationSecret,
                                       boolean requireLogin, boolean cloudEnabled, String endpoint,
                                       String accessKey, String secretKey) {
        assertThatThrownBy(() -> validate(databasePassword, applicationSecret, requireLogin, cloudEnabled,
                endpoint, accessKey, secretKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(message);
    }

    private static void validate(String databasePassword, String applicationSecret, boolean requireLogin,
                                 boolean cloudEnabled, String endpoint, String accessKey, String secretKey) {
        ProductionCredentialGuard.validate(databasePassword, applicationSecret, requireLogin, cloudEnabled,
                endpoint, accessKey, secretKey);
    }
}
