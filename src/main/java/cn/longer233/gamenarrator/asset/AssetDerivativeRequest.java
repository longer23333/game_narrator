package cn.longer233.gamenarrator.asset;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record AssetDerivativeRequest(
        @Pattern(regexp = "(?i)FRAME|AUDIO") String mode,
        @PositiveOrZero Double timestampSeconds
) { }
