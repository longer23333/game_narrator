package cn.longer233.gamenarrator.subtitle;

public record SubtitleRenderOptions(String template, boolean animated, boolean keywordHighlights) {
    public static SubtitleRenderOptions defaults(String template) {
        return new SubtitleRenderOptions(template, true, true);
    }
}
