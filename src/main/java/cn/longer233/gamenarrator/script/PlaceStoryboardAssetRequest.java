package cn.longer233.gamenarrator.script;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record PlaceStoryboardAssetRequest(
        @NotNull UUID assetId,
        @Size(max = 500) String instruction,
        boolean aiAssign) { }
