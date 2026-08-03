package cn.longer233.gamenarrator.script;

import java.util.List;

public record ScriptDocumentView(
        String title,
        String synopsis,
        String fullNarration,
        List<ScriptSegment> segments
) {
}
