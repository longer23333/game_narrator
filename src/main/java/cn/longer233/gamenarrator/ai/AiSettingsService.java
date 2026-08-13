package cn.longer233.gamenarrator.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import cn.longer233.gamenarrator.identity.CurrentUserContext;
import cn.longer233.gamenarrator.identity.LocalSecretCipher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class AiSettingsService {
    private final ObjectMapper mapper;
    private final Path file;
    private final String apiKeyOverride;
    private final JdbcTemplate jdbc;
    private final CurrentUserContext currentUser;
    private final LocalSecretCipher cipher;

    public AiSettingsService(ObjectMapper mapper,
            @Value("${game-narrator.data-root:${GAME_NARRATOR_DATA_ROOT:./data}}") String dataRoot,
            @Value("${GAME_NARRATOR_AI_API_KEY:}") String apiKeyOverride,
            JdbcTemplate jdbc, CurrentUserContext currentUser, LocalSecretCipher cipher) {
        this.mapper = mapper;
        this.file = Path.of(dataRoot).toAbsolutePath().normalize().resolve("config").resolve("ai-settings.json");
        this.apiKeyOverride = apiKeyOverride == null ? "" : apiKeyOverride.trim();
        this.jdbc=jdbc; this.currentUser=currentUser; this.cipher=cipher;
    }

    public synchronized Settings current() {
        Settings stored = stored();
        if (apiKeyOverride.isBlank()) return stored;
        return new Settings(stored.mode(), stored.provider(), apiKeyOverride, stored.baseUrl(),
                stored.visionModel(), stored.textModel(), stored.inputPricePerMillion(),
                stored.outputPricePerMillion(), stored.cachedInputPricePerMillion());
    }

    private Settings stored() {
        if (currentUser.authenticated()) {
            var rows=jdbc.query("SELECT mode,provider,base_url,vision_model,text_model,api_key_ciphertext,input_price_per_million,output_price_per_million,cached_input_price_per_million FROM user_cloud_ai_config WHERE user_id=?",(rs,n)->new Settings(rs.getString(1),rs.getString(2),cipher.decrypt(rs.getString(6)),rs.getString(3),rs.getString(4),rs.getString(5),rs.getDouble(7),rs.getDouble(8),rs.getDouble(9)),currentUser.userId());
            return rows.isEmpty()?defaults():normalize(rows.getFirst());
        }
        try {
            if (Files.isRegularFile(file)) return normalize(mapper.readValue(file.toFile(), Settings.class));
        } catch (Exception ignored) { }
        return defaults();
    }

    public synchronized Settings save(Settings requested) {
        try {
            Settings existing = stored();
            String key = requested.apiKey() == null || requested.apiKey().isBlank()
                    ? existing.apiKey() : requested.apiKey().trim();
            Settings value = normalize(new Settings(requested.mode(), requested.provider(), key,
                    requested.baseUrl(), requested.visionModel(), requested.textModel(), requested.inputPricePerMillion(),
                    requested.outputPricePerMillion(), requested.cachedInputPricePerMillion()));
            if (currentUser.authenticated()) {
                int changed=jdbc.update("UPDATE user_cloud_ai_config SET mode=?,provider=?,base_url=?,vision_model=?,text_model=?,api_key_ciphertext=?,api_key_hint=?,input_price_per_million=?,output_price_per_million=?,cached_input_price_per_million=?,updated_at=? WHERE user_id=?",value.mode(),value.provider(),value.baseUrl(),value.visionModel(),value.textModel(),cipher.encrypt(value.apiKey()),hint(value.apiKey()),value.inputPricePerMillion(),value.outputPricePerMillion(),value.cachedInputPricePerMillion(),java.time.Instant.now(),currentUser.userId());
                if(changed==0)jdbc.update("INSERT INTO user_cloud_ai_config(user_id,mode,provider,base_url,vision_model,text_model,api_key_ciphertext,api_key_hint,input_price_per_million,output_price_per_million,cached_input_price_per_million,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",currentUser.userId(),value.mode(),value.provider(),value.baseUrl(),value.visionModel(),value.textModel(),cipher.encrypt(value.apiKey()),hint(value.apiKey()),value.inputPricePerMillion(),value.outputPricePerMillion(),value.cachedInputPricePerMillion(),java.time.Instant.now());
                return value;
            }
            Files.createDirectories(file.getParent());
            Path temporary = Files.createTempFile(file.getParent(), "ai-settings-", ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return apiKeyOverride.isBlank() ? value : new Settings(value.mode(), value.provider(),
                    apiKeyOverride, value.baseUrl(), value.visionModel(), value.textModel(),
                    value.inputPricePerMillion(), value.outputPricePerMillion(), value.cachedInputPricePerMillion());
        } catch (Exception exception) {
            throw new IllegalStateException("无法保存 AI 设置：" + exception.getMessage(), exception);
        }
    }

    public synchronized PublicSettings switchLocalModel(String role, String model) {
        Settings current = stored();
        String normalizedRole = role == null ? "" : role.trim().toUpperCase();
        String name = LocalModelCatalogService.validateName(model);
        Settings changed = switch (normalizedRole) {
            case "VISION" -> new Settings("LOCAL", current.provider(), current.apiKey(), current.baseUrl(), name,
                    current.textModel(), current.inputPricePerMillion(), current.outputPricePerMillion(),
                    current.cachedInputPricePerMillion());
            case "TEXT" -> new Settings("LOCAL", current.provider(), current.apiKey(), current.baseUrl(),
                    current.visionModel(), name, current.inputPricePerMillion(), current.outputPricePerMillion(),
                    current.cachedInputPricePerMillion());
            default -> throw new IllegalArgumentException("模型角色必须是 VISION 或 TEXT");
        };
        save(changed);
        return publicView();
    }
    private String hint(String key){return key==null||key.isBlank()?null:key.substring(0,Math.min(4,key.length()));}

    public PublicSettings publicView() {
        Settings value = current();
        String masked = value.apiKey().isBlank() ? "" : "已配置（" + value.apiKey().substring(0, Math.min(4, value.apiKey().length())) + "••••）";
        return new PublicSettings(value.mode(), value.provider(), masked, !value.apiKey().isBlank(),
                value.baseUrl(), value.visionModel(), value.textModel(),value.inputPricePerMillion(),
                value.outputPricePerMillion(),value.cachedInputPricePerMillion());
    }

    private Settings normalize(Settings value) {
        String mode = "LOCAL".equalsIgnoreCase(value.mode()) ? "LOCAL" : "CLOUD";
        String provider = blank(value.provider(), "DASHSCOPE").toUpperCase();
        boolean deepSeek = "DEEPSEEK".equals(provider);
        boolean local = "LOCAL".equals(mode);
        return new Settings(mode, provider, blank(value.apiKey(), ""),
                blank(value.baseUrl(), deepSeek ? "https://api.deepseek.com" : "https://dashscope.aliyuncs.com/compatible-mode/v1"),
                blank(value.visionModel(), local || deepSeek ? "qwen2.5vl:3b" : "qwen-vl-plus"),
                blank(value.textModel(), local ? "qwen2.5:3b" : deepSeek ? "deepseek-chat" : "qwen-plus"), positive(value.inputPricePerMillion()),
                positive(value.outputPricePerMillion()),positive(value.cachedInputPricePerMillion()));
    }

    private Settings defaults() { return normalize(new Settings("CLOUD", "DASHSCOPE", "", "", "", "",0d,0d,0d)); }
    private String blank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private double positive(Double value){return value==null?0:Math.max(0,value);}

    public record Settings(String mode, String provider, String apiKey, String baseUrl,
                           String visionModel, String textModel,Double inputPricePerMillion,
                           Double outputPricePerMillion,Double cachedInputPricePerMillion) { }
    public record PublicSettings(String mode, String provider, String apiKeyMasked, boolean apiKeyConfigured,
                                 String baseUrl, String visionModel, String textModel,double inputPricePerMillion,
                                 double outputPricePerMillion,double cachedInputPricePerMillion) { }
}
