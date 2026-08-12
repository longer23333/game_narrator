package cn.longer233.gamenarrator;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.util.Base64;

/** Rejects incomplete remote-deployment credentials before Spring creates application beans. */
final class ProductionCredentialGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        Environment environment = context.getEnvironment();
        if (!environment.matchesProfiles("postgresql")) {
            return;
        }
        validate(
                environment.getProperty("spring.datasource.password"),
                environment.getProperty("game-narrator.security.master-key"),
                environment.getProperty("game-narrator.auth.require-login", Boolean.class, true),
                environment.getProperty("game-narrator.cloud-sync.enabled", Boolean.class, false),
                environment.getProperty("game-narrator.cloud-sync.endpoint"),
                environment.getProperty("game-narrator.cloud-sync.access-key"),
                environment.getProperty("game-narrator.cloud-sync.secret-key"));
    }

    static void validate(String databasePassword, String applicationSecret, boolean requireLogin,
                         boolean cloudEnabled, String endpoint, String accessKey, String secretKey) {
        requireConfigured("POSTGRES_PASSWORD", databasePassword);
        if (!requireLogin) {
            throw new IllegalStateException("PostgreSQL 远程部署必须启用 REQUIRE_LOGIN=true");
        }
        requireConfigured("GAME_NARRATOR_SECRET_KEY", applicationSecret);
        try {
            if (Base64.getDecoder().decode(applicationSecret.strip()).length != 32) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("GAME_NARRATOR_SECRET_KEY 必须是 32 字节密钥的 Base64 编码", exception);
        }
        if (cloudEnabled) {
            requireConfigured("OBJECT_STORAGE_ENDPOINT", endpoint);
            requireConfigured("OBJECT_STORAGE_ACCESS_KEY", accessKey);
            requireConfigured("OBJECT_STORAGE_SECRET_KEY", secretKey);
        }
    }

    private static void requireConfigured(String variable, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("PostgreSQL/cloud 部署缺少必需环境变量 " + variable);
        }
    }
}
