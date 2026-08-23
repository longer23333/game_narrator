package cn.longer233.gamenarrator.identity;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthSessionService {
    private final JdbcTemplate jdbc;
    private final PasswordHasher passwords;
    private final LoginAttemptThrottle loginThrottle;
    private final String administratorBootstrapToken;
    private final SecureRandom random = new SecureRandom();

    public AuthSessionService(JdbcTemplate jdbc, PasswordHasher passwords, LoginAttemptThrottle loginThrottle,
                              @Value("${game-narrator.auth.administrator-bootstrap-token:}") String administratorBootstrapToken) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.loginThrottle = loginThrottle;
        this.administratorBootstrapToken = administratorBootstrapToken == null ? "" : administratorBootstrapToken.strip();
    }

    @Transactional
    public synchronized LoginResult register(String username, String email, String displayName, String password,
                                             String bootstrapToken,
                                             String clientName, boolean rememberMe) {
        String normalized = normalizeUsername(username);
        validatePassword(password);
        String normalizedEmail = email == null || email.isBlank() ? null : email.strip().toLowerCase(Locale.ROOT);
        String shownName = displayName == null || displayName.isBlank() ? normalized : displayName.strip();
        if (shownName.length() > 100) throw new IllegalArgumentException("显示名称不能超过 100 个字符");
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Integer administrators = jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE account_type='REGISTERED' AND role='ADMIN'", Integer.class);
        String role = administrators != null && administrators == 0 && validBootstrapToken(bootstrapToken) ? "ADMIN" : "USER";
        try {
            jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,role,status,created_at,updated_at,email,account_type) VALUES(?,?,?,?,?,'ACTIVE',?,?,?,'REGISTERED')",
                    id, normalized, shownName, passwords.hash(password), role, now, now, normalizedEmail);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("用户名或邮箱已被使用");
        }
        return createSession(new Account(id, normalized, shownName, role, "ACTIVE", "REGISTERED"), clientName, rememberMe);
    }

    @Transactional
    public LoginResult login(String username, String password, String clientName, boolean rememberMe) {
        String normalized = normalizeUsername(username);
        loginThrottle.check(normalized, clientName);
        List<AccountWithPassword> matches = jdbc.query("SELECT id,username,display_name,role,status,account_type,password_hash FROM app_user WHERE LOWER(username)=?",
                (rs, row) -> new AccountWithPassword(new Account(rs.getObject("id", UUID.class), rs.getString("username"),
                        rs.getString("display_name"), rs.getString("role"), rs.getString("status"), rs.getString("account_type")),
                        rs.getString("password_hash")), normalized);
        if (matches.isEmpty() || !"ACTIVE".equals(matches.getFirst().account().status())
                || !passwords.verify(password == null ? "" : password, matches.getFirst().passwordHash())) {
            loginThrottle.failed(normalized, clientName);
            throw new IllegalArgumentException("用户名或密码错误，或账号已停用");
        }
        loginThrottle.succeeded(normalized, clientName);
        Instant now = Instant.now();
        jdbc.update("UPDATE app_user SET last_login_at=?,last_seen_at=?,updated_at=? WHERE id=?", now, now, now, matches.getFirst().account().id());
        return createSession(matches.getFirst().account(), clientName, rememberMe);
    }

    @Transactional
    public LoginResult createSession(Account account, String clientName, boolean rememberMe) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();
        jdbc.update("INSERT INTO user_session(id,user_id,token_sha256,client_name,created_at,expires_at,last_seen_at) VALUES(?,?,?,?,?,?,?)",
                UUID.randomUUID(), account.id(), sha256(token), safeClient(clientName), now,
                now.plus(sessionLifetime(rememberMe)), now);
        return new LoginResult(token, account);
    }

    @Transactional
    public Account resolve(String token) {
        if (token == null || token.isBlank()) return null;
        List<Account> rows = jdbc.query("SELECT u.id,u.username,u.display_name,u.role,u.status,u.account_type FROM user_session s JOIN app_user u ON u.id=s.user_id WHERE s.token_sha256=? AND s.revoked_at IS NULL AND s.expires_at>? AND u.status='ACTIVE'",
                (rs, row) -> new Account(rs.getObject("id", UUID.class), rs.getString("username"), rs.getString("display_name"),
                        rs.getString("role"), rs.getString("status"), rs.getString("account_type")), sha256(token), Instant.now());
        if (rows.isEmpty()) return null;
        Instant now = Instant.now();
        jdbc.update("UPDATE user_session SET last_seen_at=? WHERE token_sha256=?", now, sha256(token));
        jdbc.update("UPDATE app_user SET last_seen_at=? WHERE id=?", now, rows.getFirst().id());
        return rows.getFirst();
    }

    public void revoke(String token) {
        if (token != null && !token.isBlank()) jdbc.update("UPDATE user_session SET revoked_at=? WHERE token_sha256=?", Instant.now(), sha256(token));
    }

    public Account account(UUID id) {
        return jdbc.queryForObject("SELECT id,username,display_name,role,status,account_type FROM app_user WHERE id=?",
                (rs, row) -> new Account(rs.getObject("id", UUID.class), rs.getString("username"), rs.getString("display_name"),
                        rs.getString("role"), rs.getString("status"), rs.getString("account_type")), id);
    }

    private String normalizeUsername(String value) {
        String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_\\-]{3,64}")) throw new IllegalArgumentException("用户名需为 3-64 位字母、数字、下划线或短横线");
        return normalized;
    }
    private void validatePassword(String value) {
        if (value == null || value.length() < 8 || value.length() > 128) throw new IllegalArgumentException("密码长度需为 8-128 位");
    }
    private boolean validBootstrapToken(String supplied) {
        if (administratorBootstrapToken.isBlank() || supplied == null || supplied.isBlank()) return false;
        return MessageDigest.isEqual(administratorBootstrapToken.getBytes(StandardCharsets.UTF_8),
                supplied.strip().getBytes(StandardCharsets.UTF_8));
    }
    private String safeClient(String value) { return value == null || value.isBlank() ? "GameNarrator" : value.strip().substring(0, Math.min(120, value.strip().length())); }
    static Duration sessionLifetime(boolean rememberMe) {
        return rememberMe ? Duration.ofDays(30) : Duration.ofHours(12);
    }
    private String sha256(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("无法生成会话摘要", exception); }
    }
    public record Account(UUID id, String username, String displayName, String role, String status, String accountType) {}
    private record AccountWithPassword(Account account, String passwordHash) {}
    public record LoginResult(String token, Account account) {}
}
