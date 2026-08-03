package cn.longer233.gamenarrator.subtitle;

import cn.longer233.gamenarrator.timeline.TimelineSegment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AssSubtitleBuilder {

    public String build(List<TimelineSegment> segments, String theme) {
        Style style = style(theme);
        StringBuilder ass = new StringBuilder("""
                [Script Info]
                ScriptType: v4.00+
                PlayResX: 1920
                PlayResY: 1080
                WrapStyle: 2
                ScaledBorderAndShadow: yes

                [V4+ Styles]
                Format: Name,Fontname,Fontsize,PrimaryColour,SecondaryColour,OutlineColour,BackColour,Bold,Italic,Underline,StrikeOut,ScaleX,ScaleY,Spacing,Angle,BorderStyle,Outline,Shadow,Alignment,MarginL,MarginR,MarginV,Encoding
                """);
        ass.append("Style: Default,Microsoft YaHei,").append(style.fontSize)
                .append(',').append(style.primaryColor)
                .append(",&H0000FFFF,&H00101018,&H90000000,")
                .append(style.bold ? "-1" : "0")
                .append(",0,0,0,100,100,1,0,1,")
                .append(style.outline).append(",1,2,80,80,85,1\n\n")
                .append("[Events]\n")
                .append("Format: Layer,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text\n");

        for (TimelineSegment segment : segments) {
            String text = clean(segment.subtitle() == null || segment.subtitle().isBlank()
                    ? segment.narration() : segment.subtitle());
            String animation = switch (style.animation) {
                case "POP" -> "{\\fad(80,100)\\fscx70\\fscy70\\t(0,180,\\fscx100\\fscy100)}";
                case "TYPEWRITER" -> "{\\fad(180,160)\\blur0.5}";
                case "IMPACT" -> "{\\fad(50,80)\\bord5\\fscx115\\fscy115\\t(0,140,\\fscx100\\fscy100)}";
                default -> "{\\fad(120,100)}";
            };
            ass.append("Dialogue: 0,").append(time(segment.outputStartSeconds()))
                    .append(',').append(time(segment.outputEndSeconds()))
                    .append(",Default,,0,0,0,,").append(animation)
                    .append(wrap(text, 16)).append('\n');
        }
        return ass.toString();
    }

    private Style style(String theme) {
        return switch (theme == null ? "" : theme) {
            case "IMPACT_RED" -> new Style(62, "&H002D5BFF", true, 4, "IMPACT");
            case "COMEDY_POP" -> new Style(58, "&H004DE6F7", true, 4, "POP");
            case "TYPEWRITER_DARK" -> new Style(48, "&H00F2F2F2", false, 3, "TYPEWRITER");
            case "CLEAN_WHITE" -> new Style(46, "&H00FFFFFF", false, 2, "FADE");
            default -> new Style(56, "&H00FFFFFF", true, 4, "POP");
        };
    }

    private String wrap(String value, int maxChars) {
        if (value.length() <= maxChars) return value;
        int split = Math.min(maxChars, value.length());
        for (int index = split; index > Math.max(6, split - 6); index--) {
            if ("，。！？、；： ".indexOf(value.charAt(index - 1)) >= 0) {
                split = index;
                break;
            }
        }
        return value.substring(0, split) + "\\N" + value.substring(split);
    }

    private String clean(String value) {
        return value.replace("\\", "").replace("{", "（").replace("}", "）")
                .replace("\r", " ").replace("\n", " ").trim();
    }

    private String time(double seconds) {
        long centiseconds = Math.round(seconds * 100);
        return "%d:%02d:%02d.%02d".formatted(centiseconds / 360000,
                centiseconds / 6000 % 60, centiseconds / 100 % 60, centiseconds % 100);
    }

    private record Style(int fontSize, String primaryColor, boolean bold, int outline, String animation) {}
}
