package cn.longer233.gamenarrator.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai-settings")
public class AiSettingsController {
    private final AiSettingsService service;
    private final AdaptiveAiChatClient chatClient;
    private final AiUsageService usageService;
    private final LocalModelCatalogService localModels;

    public AiSettingsController(AiSettingsService service, AdaptiveAiChatClient chatClient, AiUsageService usageService,
                                LocalModelCatalogService localModels) {
        this.service = service;
        this.chatClient = chatClient;
        this.usageService = usageService;
        this.localModels = localModels;
    }

    @GetMapping
    public AiSettingsService.PublicSettings get() { return service.publicView(); }

    @PutMapping
    public AiSettingsService.PublicSettings update(@Valid @RequestBody Update request) {
        service.save(new AiSettingsService.Settings(request.mode(), request.provider(), request.apiKey(),
                request.baseUrl(), request.visionModel(), request.textModel(),request.inputPricePerMillion(),
                request.outputPricePerMillion(),request.cachedInputPricePerMillion()));
        return service.publicView();
    }

    @PostMapping("/test")
    public Map<String, Object> test() throws Exception {
        chatClient.chatJson("只返回 JSON：{\"connected\":true}", List.of(), false, Duration.ofSeconds(30));
        return Map.of("connected", true, "model", chatClient.activeModel(false));
    }

    @GetMapping("/usage")
    public AiUsageService.UsageSnapshot usage(){return usageService.snapshot();}

    @GetMapping("/runtime")
    public Map<String, Object> runtime() {
        var settings = service.publicView();
        return Map.of(
                "mode", settings.mode(),
                "provider", settings.provider(),
                "activeTextModel", chatClient.activeModel(false),
                "activeVisionModel", chatClient.activeModel(true),
                "local", "LOCAL".equals(settings.mode())
        );
    }

    @GetMapping("/models")
    public LocalModelCatalogService.Catalog models() {
        return localModels.catalog(chatClient.activeModel(true), chatClient.activeModel(false));
    }

    @PostMapping("/models/download")
    public LocalModelCatalogService.DownloadResult download(@RequestBody ModelDownload request) {
        return localModels.download(request.name());
    }

    @PostMapping("/models/switch")
    public AiSettingsService.PublicSettings switchModel(@RequestBody ModelSwitch request) {
        if (!localModels.catalog(chatClient.activeModel(true), chatClient.activeModel(false)).installed().stream()
                .anyMatch(model -> model.name().equals(request.name())))
            throw new IllegalArgumentException("只能切换到已安装且健康的模型");
        return service.switchLocalModel(request.role(), request.name());
    }

    public record Update(@Pattern(regexp = "LOCAL|CLOUD") String mode, String provider, String apiKey,
                         String baseUrl, String visionModel, String textModel,Double inputPricePerMillion,
                         Double outputPricePerMillion,Double cachedInputPricePerMillion) { }
    public record ModelDownload(String name) { }
    public record ModelSwitch(String role, String name) { }
}
