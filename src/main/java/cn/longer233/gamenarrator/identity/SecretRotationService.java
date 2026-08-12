package cn.longer233.gamenarrator.identity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import cn.longer233.gamenarrator.admin.AdminAccessDeniedException;

/** Re-encrypts every database credential with the configured active key version. */
@Service
public class SecretRotationService {
    private final JdbcTemplate jdbc;
    private final LocalSecretCipher cipher;
    private final CurrentUserContext currentUser;

    public SecretRotationService(JdbcTemplate jdbc, LocalSecretCipher cipher, CurrentUserContext currentUser) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.currentUser = currentUser;
    }

    @Transactional
    public RotationResult rotateAll() {
        if (!"ADMIN".equalsIgnoreCase(currentUser.role())) throw new AdminAccessDeniedException();
        int cloud = rotate("user_cloud_ai_config", "user_id", "api_key_ciphertext");
        int assets = rotateComposite();
        return new RotationResult(cipher.activeVersion(), cloud + assets, cloud, assets);
    }

    private int rotate(String table, String idColumn, String secretColumn) {
        List<Row> rows = jdbc.query("SELECT " + idColumn + "," + secretColumn + " FROM " + table,
                (rs, row) -> new Row(rs.getObject(1), rs.getString(2)));
        int changed = 0;
        for (Row row : rows) if (cipher.needsRotation(row.secret())) {
            changed += jdbc.update("UPDATE " + table + " SET " + secretColumn + "=? WHERE " + idColumn + "=?",
                    cipher.rotate(row.secret()), row.id());
        }
        return changed;
    }

    private int rotateComposite() {
        List<ProviderRow> rows = jdbc.query("SELECT user_id,provider,api_key_ciphertext FROM user_asset_provider_config",
                (rs, row) -> new ProviderRow(rs.getObject(1), rs.getString(2), rs.getString(3)));
        int changed = 0;
        for (ProviderRow row : rows) if (cipher.needsRotation(row.secret())) {
            changed += jdbc.update("UPDATE user_asset_provider_config SET api_key_ciphertext=? WHERE user_id=? AND provider=?",
                    cipher.rotate(row.secret()), row.userId(), row.provider());
        }
        return changed;
    }

    private record Row(Object id, String secret) { }
    private record ProviderRow(Object userId, String provider, String secret) { }
    public record RotationResult(String activeVersion, int rotated, int cloudAiCredentials, int assetProviderCredentials) { }
}
