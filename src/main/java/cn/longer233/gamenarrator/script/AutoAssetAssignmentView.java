package cn.longer233.gamenarrator.script;

import java.util.List;

public record AutoAssetAssignmentView(
        int assignedCount, boolean bilibiliLoginRequired, List<String> warnings,
        List<StoryboardAssetPlacementView> placements) { }
