package cn.longer233.gamenarrator.asset;

import cn.longer233.gamenarrator.identity.CurrentUserContext;
import cn.longer233.gamenarrator.identity.LocalSecretCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AssetProviderCredentialService {
    private static final List<String> PROVIDERS = List.of("PEXELS", "PIXABAY");
    private final JdbcTemplate jdbc;
    private final CurrentUserContext currentUser;
    private final LocalSecretCipher cipher;
    private final Map<String, String> environmentKeys;

    public AssetProviderCredentialService(JdbcTemplate jdbc, CurrentUserContext currentUser,
                                          LocalSecretCipher cipher,
                                          @Value("${game-narrator.asset-library.pexels.api-key:}") String pexelsKey,
                                          @Value("${game-narrator.asset-library.pixabay.api-key:}") String pixabayKey) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.cipher = cipher;
        this.environmentKeys = Map.of("PEXELS", clean(pexelsKey), "PIXABAY", clean(pixabayKey));
    }

    public List<AssetProviderCredentialView> status() {
        return PROVIDERS.stream().map(this::status).toList();
    }

    public AssetProviderCredentialView status(String provider) {
        String normalized = provider(provider);
        var rows = jdbc.query("""
                SELECT api_key_hint,updated_at FROM user_asset_provider_config
                WHERE user_id=? AND provider=?
                """, (rs, row) -> new AssetProviderCredentialView(normalized, true, "ACCOUNT",
                rs.getString("api_key_hint"), rs.getObject("updated_at", OffsetDateTime.class)),
                currentUser.userId(), normalized);
        if (!rows.isEmpty()) return rows.getFirst();
        String environment = environmentKeys.get(normalized);
        return new AssetProviderCredentialView(normalized, !environment.isBlank(),
                environment.isBlank() ? "MISSING" : "ENVIRONMENT", hint(environment), null);
    }

    public String effectiveKey(String provider) {
        String normalized = provider(provider);
        var rows = jdbc.queryForList("""
                SELECT api_key_ciphertext FROM user_asset_provider_config WHERE user_id=? AND provider=?
                """, String.class, currentUser.userId(), normalized);
        return rows.isEmpty() ? environmentKeys.get(normalized) : cipher.decrypt(rows.getFirst());
    }

    @Transactional
    public AssetProviderCredentialView save(AssetProviderCredentialRequest request) {
        String provider = provider(request.provider());
        String key = clean(request.apiKey());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int changed = jdbc.update("""
                UPDATE user_asset_provider_config SET api_key_ciphertext=?,api_key_hint=?,updated_at=?
                WHERE user_id=? AND provider=?
                """, cipher.encrypt(key), hint(key), now, currentUser.userId(), provider);
        if (changed == 0) jdbc.update("""
                INSERT INTO user_asset_provider_config(user_id,provider,api_key_ciphertext,api_key_hint,updated_at)
                VALUES(?,?,?,?,?)
                """, currentUser.userId(), provider, cipher.encrypt(key), hint(key), now);
        return status(provider);
    }

    @Transactional
    public AssetProviderCredentialView delete(String provider) {
        String normalized = provider(provider);
        jdbc.update("DELETE FROM user_asset_provider_config WHERE user_id=? AND provider=?",
                currentUser.userId(), normalized);
        return status(normalized);
    }

    private String provider(String value) {
        String normalized = clean(value).toUpperCase(Locale.ROOT);
        if (!PROVIDERS.contains(normalized)) throw new IllegalArgumentException("不支持的素材提供商：" + value);
        return normalized;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String hint(String key) {
        if (key == null || key.isBlank()) return "";
        return key.length() <= 4 ? "****" : "****" + key.substring(key.length() - 4);
    }
}
