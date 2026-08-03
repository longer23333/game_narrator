package cn.longer233.gamenarrator.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AssetReferenceRequest(
        @Pattern(regexp = "OPENVERSE|WIKIMEDIA|BILIBILI|YOUTUBE|DOUYIN|TIKTOK|USER_REFERENCE") String provider,
        @NotBlank @Pattern(regexp = "https://.+") String sourceUrl,
        String previewUrl,
        String downloadUrl,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 120) String creator,
        @NotBlank @Pattern(regexp = "SFX|BGM|IMAGE|MEME|VIDEO") String assetType,
        @NotBlank @Size(max = 40) String licenseCode,
        String licenseUrl,
        @Size(max = 500) String attribution,
        List<@Size(max = 100) String> platformTags
) {
}
