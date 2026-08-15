package cn.longer233.gamenarrator.asset;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssetProviderContractionTest {
    @Test
    void acceptsOnlyCurrentDiscoveryProviders() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(request("WIKIMEDIA"))).isEmpty();
            assertThat(validator.validate(request("BILIBILI"))).isEmpty();
            assertThat(validator.validate(request("OPENVERSE"))).isNotEmpty();
            assertThat(validator.validate(request("PEXELS"))).isNotEmpty();
            assertThat(validator.validate(request("PIXABAY"))).isNotEmpty();
        }
    }

    private AssetSearchRequest request(String provider) {
        return new AssetSearchRequest("battle", "IMAGE", 9, 1, true, true, provider, "RELEVANCE");
    }
}
