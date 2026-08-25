package cn.longer233.gamenarrator.creative;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record GeneratedImageRequest(@NotBlank String kind, @NotBlank String prompt, String negativePrompt,
                                    UUID characterReferenceAssetId, UUID styleReferenceAssetId,
                                    @Min(256) @Max(1536) Integer width, @Min(256) @Max(1536) Integer height,
                                    @Min(1) @Max(50) Integer steps, Long seed, String sourceUrl, String sourceAttribution) {}
