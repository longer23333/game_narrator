package cn.longer233.gamenarrator.script;

import java.util.List;

public record GeneratedScript(
        String title,
        String synopsis,
        String fullNarration,
        String scriptPath,
        List<ScriptSegment> segments
) {
}
