package cn.longer233.gamenarrator.asset;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AssetSearchRequest(
        @NotBlank String query,
        @NotBlank String assetType,
        @Min(1) @Max(50) Integer pageSize,
        @Min(1) @Max(100) Integer page,
        Boolean commercialUse,
        Boolean allowModification,
        @Pattern(regexp = "(?i)OPENVERSE|WIKIMEDIA|BILIBILI|PEXELS|PIXABAY") String provider,
        @Pattern(regexp = "(?i)RELEVANCE|NEWEST|POPULAR|DANMAKU") String sort
) {
}
