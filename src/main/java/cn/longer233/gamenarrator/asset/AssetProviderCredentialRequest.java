package cn.longer233.gamenarrator.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AssetProviderCredentialRequest(
        @NotBlank @Pattern(regexp = "PEXELS|PIXABAY") String provider,
        @NotBlank @Size(min = 8, max = 500) String apiKey) {
}
