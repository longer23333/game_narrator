package cn.longer233.gamenarrator.importer;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record MediaDownloadRequest(
        @NotBlank @Pattern(regexp = "https://.+") String url,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_+\\-/\\[\\].()<>?=!*]{1,120}") String formatId,
        boolean subtitles,
        boolean addToLibrary,
        @AssertTrue(message = "必须确认拥有下载和再创作所需权利") boolean rightsConfirmed,
        @Pattern(regexp = "[0-9a-fA-F-]{36}", message = "cookieToken is invalid")
        String cookieToken,
        @Size(max = 500) String title,
        @Size(max = 300) String creator,
        @Size(max = 2000) String thumbnail,
        @PositiveOrZero Double durationSeconds,
        @Size(max = 20) List<@Size(max = 100) String> tags
) {
}
