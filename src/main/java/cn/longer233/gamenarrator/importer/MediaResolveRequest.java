package cn.longer233.gamenarrator.importer;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MediaResolveRequest(
        @NotBlank @Pattern(regexp = "https://.+") String url,
        @AssertTrue(message = "必须确认拥有下载和再创作所需权利") boolean rightsConfirmed,
        @Pattern(regexp = "[0-9a-fA-F-]{36}", message = "cookieToken is invalid")
        String cookieToken
) {
}
