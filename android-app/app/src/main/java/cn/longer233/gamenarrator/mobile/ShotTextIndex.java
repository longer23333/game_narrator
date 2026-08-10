package cn.longer233.gamenarrator.mobile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ShotTextIndex {
    private ShotTextIndex() { }

    public static final class Segment {
        private final String key;
        private final String name;
        private final String subtitle;
        private final String narration;
        private final String effectCue;

        public Segment(String key, String name, String subtitle, String narration, String effectCue) {
            this.key = key == null ? "" : key;
            this.name = name == null ? "" : name;
            this.subtitle = subtitle == null ? "" : subtitle;
            this.narration = narration == null ? "" : narration;
            this.effectCue = effectCue == null ? "" : effectCue;
        }

        public String key() { return key; }
        public String name() { return name; }
        public String subtitle() { return subtitle; }
        public String narration() { return narration; }
        public String effectCue() { return effectCue; }
    }

    public static final class Hit {
        private final String key;
        private final int score;
        private final String fields;

        Hit(String key, int score, String fields) {
            this.key = key;
            this.score = score;
            this.fields = fields;
        }

        public String key() { return key; }
        public int score() { return score; }
        public String fields() { return fields; }
    }

    public static List<Hit> search(List<Segment> segments, String query, int maxResults) {
        List<Hit> hits = new ArrayList<>();
        if (segments == null || segments.isEmpty()) return hits;
        Map<String, Integer> queryTokens = tokens(query);
        if (queryTokens.isEmpty()) return hits;
        for (Segment segment : segments) {
            StringBuilder matchedFields = new StringBuilder();
            int score = 0;
            score += scoreField(segment.name(), queryTokens, "名称", matchedFields);
            score += scoreField(segment.subtitle(), queryTokens, "字幕", matchedFields);
            score += scoreField(segment.narration(), queryTokens, "解说", matchedFields);
            score += scoreField(segment.effectCue(), queryTokens, "特效", matchedFields);
            if (score > 0) hits.add(new Hit(segment.key(), score, matchedFields.toString()));
        }
        hits.sort(Comparator.comparingInt(Hit::score).reversed());
        if (maxResults > 0 && hits.size() > maxResults) return new ArrayList<>(hits.subList(0, maxResults));
        return hits;
    }

    private static int scoreField(String field, Map<String, Integer> queryTokens, String label, StringBuilder matchedFields) {
        if (field == null || field.isBlank()) return 0;
        Map<String, Integer> fieldTokens = tokens(field);
        int score = 0;
        for (Map.Entry<String, Integer> entry : queryTokens.entrySet()) {
            Integer present = fieldTokens.get(entry.getKey());
            if (present != null) {
                score += entry.getValue() * 100 + Math.min(present, entry.getValue()) * 10;
                if (matchedFields.length() > 0) matchedFields.append("、");
                matchedFields.append(label);
            }
        }
        return score;
    }

    private static Map<String, Integer> tokens(String value) {
        Map<String, Integer> tokens = new HashMap<>();
        if (value == null) return tokens;
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{S}]+", " ");
        StringBuilder ascii = new StringBuilder();
        List<Character> cjk = new ArrayList<>();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (isCjk(c)) {
                flushAscii(tokens, ascii);
                cjk.add(c);
            } else if (Character.isLetterOrDigit(c)) {
                ascii.append(c);
            } else {
                flushAscii(tokens, ascii);
            }
        }
        flushAscii(tokens, ascii);
        if (cjk.size() == 1) {
            addToken(tokens, String.valueOf(cjk.get(0)));
        } else {
            for (int i = 0; i + 1 < cjk.size(); i++) {
                addToken(tokens, "" + cjk.get(i) + cjk.get(i + 1));
            }
        }
        return tokens;
    }

    private static void flushAscii(Map<String, Integer> tokens, StringBuilder ascii) {
        if (ascii.length() == 0) return;
        addToken(tokens, ascii.toString());
        ascii.setLength(0);
    }

    private static void addToken(Map<String, Integer> tokens, String token) {
        if (token == null || token.isBlank()) return;
        tokens.put(token, tokens.getOrDefault(token, 0) + 1);
    }

    private static boolean isCjk(char c) {
        return (c >= '\u4E00' && c <= '\u9FFF') || (c >= '\u3400' && c <= '\u4DBF');
    }
}
