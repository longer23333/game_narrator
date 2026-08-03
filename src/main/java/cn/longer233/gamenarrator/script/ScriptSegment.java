package cn.longer233.gamenarrator.script;

public record ScriptSegment(
        int clipIndex,
        double startSeconds,
        double endSeconds,
        String narration,
        String subtitle,
        String effectCue
) {
}
