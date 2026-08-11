package cn.longer233.gamenarrator.subtitle;

import cn.longer233.gamenarrator.timeline.TimelineSegment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.ArrayList;

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
                    .append(karaoke(wrap(text, 16), segment.outputEndSeconds()
                            - segment.outputStartSeconds())).append('\n');
        }
        return ass.toString();
    }

    private String karaoke(String text, double durationSeconds) {
        List<KaraokeToken> tokens = tokenize(text);
        double totalWeight = tokens.stream().mapToDouble(KaraokeToken::weight).sum();
        if (totalWeight <= 0) return text;
        int totalCentiseconds = Math.max(1, (int) Math.round(Math.max(0, durationSeconds) * 100));
        StringBuilder result = new StringBuilder(text.length() * 2);
        double consumedWeight = 0;
        int allocated = 0;
        for (KaraokeToken token : tokens) {
            if (token.weight() <= 0) {
                result.append(token.text());
                continue;
            }
            consumedWeight += token.weight();
            int target = (int) Math.round(totalCentiseconds * consumedWeight / totalWeight);
            int tokenDuration = Math.max(0, target - allocated);
            allocated = target;
            result.append("{\\kf").append(tokenDuration).append('}').append(token.text());
        }
        return result.toString();
    }

    private List<KaraokeToken> tokenize(String text) {
        List<KaraokeToken> tokens = new ArrayList<>();
        for (int offset = 0; offset < text.length();) {
            if (offset + 1 < text.length() && text.charAt(offset) == '\\' && text.charAt(offset + 1) == 'N') {
                tokens.add(new KaraokeToken("\\N", 0));
                offset += 2;
                continue;
            }
            int codePoint = text.codePointAt(offset);
            if (Character.isWhitespace(codePoint)) {
                tokens.add(new KaraokeToken(new String(Character.toChars(codePoint)), 0));
                offset += Character.charCount(codePoint);
                continue;
            }
            if (codePoint < 128 && Character.isLetterOrDigit(codePoint)) {
                int end = offset + Character.charCount(codePoint);
                while (end < text.length()) {
                    int next = text.codePointAt(end);
                    if (next >= 128 || (!Character.isLetterOrDigit(next) && next != '\'')) break;
                    end += Character.charCount(next);
                }
                String word = text.substring(offset, end);
                tokens.add(new KaraokeToken(word, Math.max(1, Math.sqrt(word.length()))));
                offset = end;
                continue;
            }
            String value = new String(Character.toChars(codePoint));
            double weight = isPunctuation(codePoint) ? .35 : 1;
            tokens.add(new KaraokeToken(value, weight));
            offset += Character.charCount(codePoint);
        }
        return tokens;
    }

    private boolean isPunctuation(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.CONNECTOR_PUNCTUATION || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION || type == Character.END_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION;
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
    private record KaraokeToken(String text, double weight) {}
}
