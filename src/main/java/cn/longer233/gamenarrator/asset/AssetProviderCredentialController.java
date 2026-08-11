package cn.longer233.gamenarrator.asset;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assets/provider-settings")
public class AssetProviderCredentialController {
    private final AssetProviderCredentialService credentials;
    private final PexelsAssetClient pexels;
    private final PixabayAssetClient pixabay;

    public AssetProviderCredentialController(AssetProviderCredentialService credentials,
                                             PexelsAssetClient pexels, PixabayAssetClient pixabay) {
        this.credentials = credentials;
        this.pexels = pexels;
        this.pixabay = pixabay;
    }

    @GetMapping
    public List<AssetProviderCredentialView> status() { return credentials.status(); }

    @PutMapping
    public AssetProviderCredentialView save(@Valid @RequestBody AssetProviderCredentialRequest request) {
        return credentials.save(request);
    }

    @DeleteMapping("/{provider}")
    public AssetProviderCredentialView delete(@PathVariable String provider) {
        return credentials.delete(provider);
    }

    @PostMapping("/{provider}/test")
    public Map<String, Object> test(@PathVariable String provider) {
        String normalized = provider.toUpperCase(java.util.Locale.ROOT);
        String key = credentials.effectiveKey(normalized);
        if (key.isBlank()) throw new IllegalStateException(normalized + " 尚未配置 API Key");
        long started = System.nanoTime();
        AssetSearchRequest request = new AssetSearchRequest("game", "IMAGE", 1, 1,
                true, true, normalized, "RELEVANCE");
        int count = switch (normalized) {
            case "PEXELS" -> pexels.search(request, key).path("results").size();
            case "PIXABAY" -> pixabay.search(request, key).path("results").size();
            default -> throw new IllegalArgumentException("不支持的素材提供商：" + provider);
        };
        return Map.of("provider", normalized, "success", true, "resultCount", count,
                "elapsedMs", (System.nanoTime() - started) / 1_000_000);
    }
}
