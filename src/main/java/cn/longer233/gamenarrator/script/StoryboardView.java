package cn.longer233.gamenarrator.script;

import java.util.List;

public record StoryboardView(
        String title, String synopsis,
        boolean reviewEnabled, boolean approved,
        List<StoryboardSegmentView> segments
) {
}
