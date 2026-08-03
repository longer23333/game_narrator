package cn.longer233.gamenarrator.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class AiSettingsService {
    private final ObjectMapper mapper;
    private final Path file;

    public AiSettingsService(ObjectMapper mapper,
            @Value("${game-narrator.data-root:${GAME_NARRATOR_DATA_ROOT:./data}}") String dataRoot) {
        this.mapper = mapper;
        this.file = Path.of(dataRoot).toAbsolutePath().normalize().resolve("config").resolve("ai-settings.json");
    }

    public synchronized Settings current() {
        try {
            if (Files.isRegularFile(file)) return normalize(mapper.readValue(file.toFile(), Settings.class));
        } catch (Exception ignored) { }
        return defaults();
    }

    public synchronized Settings save(Settings requested) {
        try {
            Settings existing = current();
            String key = requested.apiKey() == null || requested.apiKey().isBlank()
                    ? existing.apiKey() : requested.apiKey().trim();
            Settings value = normalize(new Settings(requested.mode(), requested.provider(), key,
                    requested.baseUrl(), requested.visionModel(), requested.textModel(), requested.inputPricePerMillion(),
                    requested.outputPricePerMillion(), requested.cachedInputPricePerMillion()));
            Files.createDirectories(file.getParent());
            Path temporary = Files.createTempFile(file.getParent(), "ai-settings-", ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return value;
        } catch (Exception exception) {
            throw new IllegalStateException("无法保存 AI 设置：" + exception.getMessage(), exception);
        }
    }

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
        return new Settings(mode, provider, blank(value.apiKey(), ""),
                blank(value.baseUrl(), deepSeek ? "https://api.deepseek.com" : "https://dashscope.aliyuncs.com/compatible-mode/v1"),
                blank(value.visionModel(), deepSeek ? "qwen2.5vl:3b" : "qwen-vl-plus"),
                blank(value.textModel(), deepSeek ? "deepseek-chat" : "qwen-plus"), positive(value.inputPricePerMillion()),
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
