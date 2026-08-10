package cn.longer233.gamenarrator.mobile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory Netscape cookies.txt session. Cookies are parsed from the selected
 * file, held only in RAM for one hour and never written to storage, database
 * or logs.
 */
public final class CookieFileSession {
    public static final long LIFETIME_MS = 60 * 60 * 1000L;

    private final Map<String, String> cookiesByDomain = new HashMap<>();
    private long loadedAt;

    public void load(InputStream input) throws IOException {
        Map<String, String> parsed = parse(input, System.currentTimeMillis() / 1000L);
        if (parsed.isEmpty()) throw new IllegalArgumentException("文件中没有可用的 Cookie");
        cookiesByDomain.clear();
        cookiesByDomain.putAll(parsed);
        loadedAt = System.currentTimeMillis();
    }

    public boolean isActive() {
        return loadedAt > 0 && System.currentTimeMillis() - loadedAt <= LIFETIME_MS;
    }

    public int domainCount() {
        return cookiesByDomain.size();
    }

    public String cookiesForUrl(String url) {
        if (!isActive() || url == null) return "";
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : cookiesByDomain.entrySet()) {
            if (url.contains(entry.getKey())) {
                if (out.length() > 0) out.append("; ");
                out.append(entry.getValue());
            }
        }
        return out.toString();
    }

    static Map<String, String> parse(InputStream input, long nowSeconds) throws IOException {
        Map<String, String> out = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String value = line.trim();
                if (value.startsWith("#HttpOnly_")) value = value.substring("#HttpOnly_".length()).trim();
                if (value.isBlank() || value.startsWith("#")) continue;
                String[] parts = value.split("\\t");
                if (parts.length < 7) continue;
                long expiry;
                try {
                    expiry = Long.parseLong(parts[4]);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                if (expiry > 0 && expiry < nowSeconds) continue;
                String name = parts[5];
                String cookieValue = parts[6];
                if (name.isBlank() || cookieValue.isBlank()) continue;
                out.put(parts[0], name + "=" + cookieValue);
            }
        }
        return out;
    }
}
