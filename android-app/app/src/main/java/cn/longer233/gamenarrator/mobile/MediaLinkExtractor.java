package cn.longer233.gamenarrator.mobile;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MediaLinkExtractor {
    private MediaLinkExtractor() { }

    public static final class Candidate {
        private final String url;
        private final String mimeType;
        private final String source;

        Candidate(String url, String mimeType, String source) {
            this.url = url;
            this.mimeType = mimeType == null ? "" : mimeType;
            this.source = source;
        }

        public String url() { return url; }
        public String mimeType() { return mimeType; }
        public String source() { return source; }
    }

    public static List<Candidate> extract(String html, String baseUrl) {
        List<Candidate> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (html == null || html.isBlank()) return result;
        addCandidates(result, seen, metaContent(html, "og:video"), baseUrl, "og:video");
        addCandidates(result, seen, metaContent(html, "og:video:url"), baseUrl, "og:video:url");
        addCandidates(result, seen, metaContent(html, "og:video:secure_url"), baseUrl, "og:video:secure_url");
        addCandidates(result, seen, metaContent(html, "twitter:player:stream"), baseUrl, "twitter:player:stream");
        int index = 0;
        String lower = html.toLowerCase(Locale.ROOT);
        while (index < html.length()) {
            int video = lower.indexOf("<video", index);
            int source = lower.indexOf("<source", index);
            int next = Math.min(video < 0 ? Integer.MAX_VALUE : video, source < 0 ? Integer.MAX_VALUE : source);
            if (next == Integer.MAX_VALUE) break;
            int end = html.indexOf('>', next);
            if (end < 0) break;
            String tag = html.substring(next, end + 1);
            String src = attr(tag, "src");
            if (src != null && !src.isBlank()) {
                addCandidate(result, seen, src, html.toLowerCase(Locale.ROOT).substring(next, Math.min(next + 60, html.length())), baseUrl,
                        source >= 0 && next == source ? attr(tag, "type") : "", "html-" + (source >= 0 && next == source ? "source" : "video"));
            }
            index = end + 1;
        }
        return result;
    }

    private static void addCandidates(List<Candidate> result, Set<String> seen, List<String> values, String baseUrl, String source) {
        if (values == null) return;
        for (String value : values) addCandidate(result, seen, value, value, baseUrl, "", source);
    }

    private static void addCandidate(List<Candidate> result, Set<String> seen, String raw, String snippet, String baseUrl, String mimeType, String source) {
        if (raw == null || raw.isBlank()) return;
        String resolved = resolve(baseUrl, decode(raw));
        if (resolved == null || !seen.add(resolved)) return;
        String format = mimeType == null ? "" : mimeType;
        if (format.isBlank()) format = MediaFormatPreference.mimeFor(resolved);
        result.add(new Candidate(resolved, format, source));
    }

    private static List<String> metaContent(String html, String property) {
        List<String> values = new ArrayList<>();
        String lower = html.toLowerCase(Locale.ROOT);
        String needle = "property=\"" + property + "\"";
        int from = 0;
        while (true) {
            int found = lower.indexOf(needle, from);
            if (found < 0) break;
            int content = lower.indexOf("content=", found);
            int tagEnd = html.indexOf('>', found);
            if (content >= 0 && (tagEnd < 0 || content < tagEnd)) {
                String value = attr(html.substring(found, tagEnd < 0 ? html.length() : tagEnd + 1), "content");
                if (value != null && !value.isBlank()) values.add(value);
            }
            from = found + needle.length();
        }
        return values;
    }

    private static String attr(String tag, String name) {
        String lower = tag.toLowerCase(Locale.ROOT);
        String needle = name + "=";
        int found = lower.indexOf(needle);
        if (found < 0) return null;
        int start = found + needle.length();
        while (start < tag.length() && (tag.charAt(start) == ' ' || tag.charAt(start) == '\t' || tag.charAt(start) == '\n')) start++;
        if (start < tag.length() && tag.charAt(start) == '"') {
            int end = tag.indexOf('"', start + 1);
            return end < 0 ? tag.substring(start + 1) : tag.substring(start + 1, end);
        }
        if (start < tag.length() && tag.charAt(start) == '\'') {
            int end = tag.indexOf('\'', start + 1);
            return end < 0 ? tag.substring(start + 1) : tag.substring(start + 1, end);
        }
        int end = tag.length();
        for (int i = start; i < tag.length(); i++) {
            char c = tag.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '>') { end = i; break; }
        }
        return start < tag.length() ? tag.substring(start, end) : null;
    }

    private static String decode(String value) {
        return value.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'");
    }

    private static String resolve(String baseUrl, String value) {
        try {
            if (value.startsWith("//")) {
                URI base = URI.create(baseUrl);
                return base.getScheme() + ":" + value;
            }
            if (value.startsWith("http://") || value.startsWith("https://")) return value;
            if (baseUrl == null || baseUrl.isBlank()) return value;
            return URI.create(baseUrl).resolve(value).toString();
        } catch (Exception ignored) {
            return value;
        }
    }
}
