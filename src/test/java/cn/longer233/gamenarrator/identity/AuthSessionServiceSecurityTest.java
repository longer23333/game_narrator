package cn.longer233.gamenarrator.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthSessionServiceSecurityTest {
    private AuthSessionService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:auth-security-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE app_user(
                  id UUID PRIMARY KEY, username VARCHAR(64) UNIQUE NOT NULL, display_name VARCHAR(100) NOT NULL,
                  password_hash VARCHAR(255), role VARCHAR(20) NOT NULL, status VARCHAR(20) NOT NULL,
                  created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                  last_login_at TIMESTAMP WITH TIME ZONE, last_seen_at TIMESTAMP WITH TIME ZONE,
                  email VARCHAR(255) UNIQUE, account_type VARCHAR(20) NOT NULL)
                """);
        jdbc.execute("""
                CREATE TABLE user_session(
                  id UUID PRIMARY KEY, user_id UUID NOT NULL, token_sha256 VARCHAR(64) NOT NULL,
                  client_name VARCHAR(120), created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                  expires_at TIMESTAMP WITH TIME ZONE NOT NULL, last_seen_at TIMESTAMP WITH TIME ZONE,
                  revoked_at TIMESTAMP WITH TIME ZONE)
                """);
        service = new AuthSessionService(jdbc, new PasswordHasher(),
                new LoginAttemptThrottle(3, Duration.ofMinutes(5), 100, java.time.Clock.systemUTC()),
                "deployment-secret");
    }

    @Test
    void firstRegistrationIsNotAdminWithoutMatchingDeploymentToken() {
        var first = service.register("first_user", null, "First", "password-1", null, "client", false);
        var second = service.register("bootstrap_admin", null, "Admin", "password-2",
                "deployment-secret", "client", false);
        var third = service.register("wrong_token", null, "Wrong", "password-3",
                "not-the-secret", "client", false);

        assertThat(first.account().role()).isEqualTo("USER");
        assertThat(second.account().role()).isEqualTo("ADMIN");
        assertThat(third.account().role()).isEqualTo("USER");
    }
}
