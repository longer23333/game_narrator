package cn.longer233.gamenarrator.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import cn.longer233.gamenarrator.identity.CurrentUserContext;
import cn.longer233.gamenarrator.identity.LocalSecretCipher;
import cn.longer233.gamenarrator.common.AtomicArtifactWriter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;

@Service
public class AiSettingsService {
    private static final Map<String,String> PROVIDER_BASE_URLS=Map.ofEntries(
            Map.entry("DASHSCOPE","https://dashscope.aliyuncs.com/compatible-mode/v1"),
            Map.entry("DEEPSEEK","https://api.deepseek.com"), Map.entry("OPENAI","https://api.openai.com/v1"),
            Map.entry("ANTHROPIC","https://api.anthropic.com/v1"), Map.entry("GEMINI","https://generativelanguage.googleapis.com/v1beta"),
            Map.entry("OPENROUTER","https://openrouter.ai/api/v1"), Map.entry("SILICONFLOW","https://api.siliconflow.cn/v1"),
            Map.entry("MOONSHOT","https://api.moonshot.cn/v1"), Map.entry("ZHIPU","https://open.bigmodel.cn/api/paas/v4"),
            Map.entry("VOLCENGINE","https://ark.cn-beijing.volces.com/api/v3"), Map.entry("BAIDU","https://qianfan.baidubce.com/v2"),
            Map.entry("TENCENT","https://api.hunyuan.cloud.tencent.com/v1"), Map.entry("MINIMAX","https://api.minimax.chat/v1"),
            Map.entry("XAI","https://api.x.ai/v1"), Map.entry("MISTRAL","https://api.mistral.ai/v1"),
            Map.entry("GROQ","https://api.groq.com/openai/v1"), Map.entry("TOGETHER","https://api.together.xyz/v1"),
            Map.entry("PERPLEXITY","https://api.perplexity.ai"), Map.entry("CEREBRAS","https://api.cerebras.ai/v1"));
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
            AtomicArtifactWriter.writeJson(mapper, file, value);
            return apiKeyOverride.isBlank() ? value : new Settings(value.mode(), value.provider(),
                    apiKeyOverride, value.baseUrl(), value.visionModel(), value.textModel(),
                    value.inputPricePerMillion(), value.outputPricePerMillion(), value.cachedInputPricePerMillion());
        } catch (IllegalArgumentException exception) {
            throw exception;
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
        if (!PROVIDER_BASE_URLS.containsKey(provider) && !"OPENAI_COMPATIBLE".equals(provider)) {
            throw new IllegalArgumentException("不支持的云端 AI 服务商");
        }
        boolean deepSeek = "DEEPSEEK".equals(provider);
        boolean local = "LOCAL".equals(mode);
        String baseUrl = "OPENAI_COMPATIBLE".equals(provider)
                ? validateCompatibleBaseUrl(value.baseUrl()) : PROVIDER_BASE_URLS.get(provider);
        return new Settings(mode, provider, blank(value.apiKey(), ""),
                baseUrl,
                blank(value.visionModel(), local || deepSeek ? "qwen2.5vl:3b" : "qwen-vl-plus"),
                blank(value.textModel(), local ? "qwen2.5:3b" : deepSeek ? "deepseek-chat" : "qwen-plus"), positive(value.inputPricePerMillion()),
                positive(value.outputPricePerMillion()),positive(value.cachedInputPricePerMillion()));
    }

    private Settings defaults() { return normalize(new Settings("CLOUD", "DASHSCOPE", "", "", "", "",0d,0d,0d)); }
    private String blank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private double positive(Double value){return value==null||!Double.isFinite(value)?0:Math.max(0,value);}

    private String validateCompatibleBaseUrl(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("OpenAI 兼容服务必须填写 HTTPS 公网地址");
        URI uri;
        try { uri=URI.create(value.trim()); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("OpenAI 兼容服务地址格式无效",exception); }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null
                || uri.getFragment()!=null || uri.getQuery()!=null) {
            throw new IllegalArgumentException("OpenAI 兼容服务只允许使用不含凭据、查询参数和片段的 HTTPS 公网地址");
        }
        try {
            InetAddress[] addresses=InetAddress.getAllByName(uri.getHost());
            if(addresses.length==0) throw new IllegalArgumentException("OpenAI 兼容服务地址无法解析");
            for(InetAddress address:addresses) if(address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isSiteLocalAddress() || address.isLinkLocalAddress() || address.isMulticastAddress()) {
                throw new IllegalArgumentException("OpenAI 兼容服务禁止使用本机、内网或组播地址");
            }
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("OpenAI 兼容服务地址无法解析",exception);
        }
        return uri.toString().replaceAll("/+$","");
    }

    public record Settings(String mode, String provider, String apiKey, String baseUrl,
                           String visionModel, String textModel,Double inputPricePerMillion,
                           Double outputPricePerMillion,Double cachedInputPricePerMillion) { }
    public record PublicSettings(String mode, String provider, String apiKeyMasked, boolean apiKeyConfigured,
                                 String baseUrl, String visionModel, String textModel,double inputPricePerMillion,
                                 double outputPricePerMillion,double cachedInputPricePerMillion) { }
}
