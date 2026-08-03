package cn.longer233.gamenarrator.script;

import java.util.UUID;

public record StoryboardAssetPlacementView(
        UUID id, UUID assetId, int clipIndex, String title, String assetType,
        String placementType, String position, String instruction,
        boolean aiAssigned, boolean cutoutApplied, String previewUrl) { }
