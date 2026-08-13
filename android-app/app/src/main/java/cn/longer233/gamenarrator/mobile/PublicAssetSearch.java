package cn.longer233.gamenarrator.mobile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Anonymous public-asset search against Wikimedia Commons and Openverse.
 */
public final class PublicAssetSearch {
    private static final String WIKIMEDIA_ENDPOINT = "https://commons.wikimedia.org/w/api.php";
    private static final String OPENVERSE_IMAGES = "https://api.openverse.org/v1/images/";
    private static final String OPENVERSE_AUDIO = "https://api.openverse.org/v1/audio/";
    private static final String PEXELS_IMAGES = "https://api.pexels.com/v1/search";
    private static final String PIXABAY_IMAGES = "https://pixabay.com/api/";

    private PublicAssetSearch() { }

    public static List<PublicAsset> searchWikimedia(String query, int limit) throws IOException {
        String encoded = encode(query);
        String url = WIKIMEDIA_ENDPOINT
                + "?action=query&format=json&origin=*&generator=search&gsrsearch=" + encoded
                + "&gsrnamespace=6&gsrlimit=" + Math.max(1, Math.min(limit, 50))
                + "&prop=imageinfo&iiprop=url%7Cextmetadata%7Cmime&iiurlwidth=320";
        try {
            return parseWikimedia(httpGet(url));
        } catch (JSONException error) {
            throw new IOException("Wikimedia 返回了无法解析的结果", error);
        }
    }

    public static List<PublicAsset> searchOpenverse(String query, String mediaType, int limit) throws IOException {
        String endpoint = "image".equals(mediaType) ? OPENVERSE_IMAGES : OPENVERSE_AUDIO;
        String url = endpoint + "?q=" + encode(query) + "&page_size=" + Math.max(1, Math.min(limit, 50)) + "&page=1";
        try {
            return parseOpenverse(httpGet(url), !"image".equals(mediaType));
        } catch (JSONException error) {
            throw new IOException("Openverse 返回了无法解析的结果", error);
        }
    }

    public static List<PublicAsset> searchPexels(String query, int limit, String apiKey) throws IOException {
        requireKey(apiKey, "Pexels");
        try { return parsePexels(httpGet(PEXELS_IMAGES + "?query=" + encode(query) + "&per_page=" + bounded(limit), "Authorization", apiKey)); }
        catch (JSONException error) { throw new IOException("Pexels 返回了无法解析的结果", error); }
    }

    public static List<PublicAsset> searchPixabay(String query, int limit, String apiKey) throws IOException {
        requireKey(apiKey, "Pixabay");
        try { return parsePixabay(httpGet(PIXABAY_IMAGES + "?key=" + encode(apiKey) + "&q=" + encode(query)
                + "&image_type=photo&safesearch=true&per_page=" + bounded(limit), null, null)); }
        catch (JSONException error) { throw new IOException("Pixabay 返回了无法解析的结果", error); }
    }

    static List<PublicAsset> parsePexels(String json) throws JSONException {
        List<PublicAsset> out = new ArrayList<>();
        JSONArray values = new JSONObject(json).optJSONArray("photos");
        if (values == null) return out;
        for (int i = 0; i < values.length(); i++) {
            JSONObject value = values.optJSONObject(i); if (value == null) continue;
            JSONObject src = value.optJSONObject("src");
            out.add(new PublicAsset("PEXELS", value.optString("alt", "Pexels photo"),
                    value.optString("photographer", ""), "Pexels License", "https://www.pexels.com/license/",
                    value.optString("url", ""), src == null ? "" : src.optString("medium", ""),
                    src == null ? "" : src.optString("original", ""), "image"));
        }
        return out;
    }

    static List<PublicAsset> parsePixabay(String json) throws JSONException {
        List<PublicAsset> out = new ArrayList<>();
        JSONArray values = new JSONObject(json).optJSONArray("hits");
        if (values == null) return out;
        for (int i = 0; i < values.length(); i++) {
            JSONObject value = values.optJSONObject(i); if (value == null) continue;
            out.add(new PublicAsset("PIXABAY", value.optString("tags", "Pixabay image"), value.optString("user", ""),
                    "Pixabay Content License", "https://pixabay.com/service/license-summary/",
                    value.optString("pageURL", ""), value.optString("previewURL", ""),
                    value.optString("largeImageURL", value.optString("webformatURL", "")), "image"));
        }
        return out;
    }

    static List<PublicAsset> parseWikimedia(String json) throws JSONException {
        List<PublicAsset> out = new ArrayList<>();
        JSONObject root = new JSONObject(json);
        JSONObject query = root.optJSONObject("query");
        if (query == null) return out;
        JSONObject pages = query.optJSONObject("pages");
        if (pages == null) return out;
        Iterator<String> keys = pages.keys();
        while (keys.hasNext()) {
            JSONObject page = pages.optJSONObject(keys.next());
            if (page == null) continue;
            JSONArray imageInfo = page.optJSONArray("imageinfo");
            if (imageInfo == null || imageInfo.length() == 0) continue;
            JSONObject info = imageInfo.optJSONObject(0);
            if (info == null) continue;
            JSONObject meta = info.optJSONObject("extmetadata");
            out.add(new PublicAsset("WIKIMEDIA", page.optString("title"), text(meta, "Artist"),
                    text(meta, "LicenseShortName"), text(meta, "LicenseUrl"), page.optString("descriptionurl"),
                    info.optString("thumburl"), info.optString("url"), mediaTypeFromMime(info.optString("mime"))));
        }
        return out;
    }

    static List<PublicAsset> parseOpenverse(String json, boolean audio) throws JSONException {
        List<PublicAsset> out = new ArrayList<>();
        JSONObject root = new JSONObject(json);
        JSONArray results = root.optJSONArray("results");
        if (results == null) return out;
        for (int i = 0; i < results.length(); i++) {
            JSONObject value = results.optJSONObject(i);
            if (value == null) continue;
            String license = value.optString("license", "");
            String licenseUrl = value.optString("license_url", "");
            if (licenseUrl.isBlank() && !license.isBlank()) {
                licenseUrl = "https://creativecommons.org/licenses/" + license + "/";
            }
            out.add(new PublicAsset("OPENVERSE", value.optString("title", ""), value.optString("creator", ""),
                    license, licenseUrl, value.optString("foreign_landing_url", ""), value.optString("thumbnail", ""),
                    value.optString("url", ""), audio ? "audio" : "image"));
        }
        return out;
    }

    private static String text(JSONObject meta, String key) {
        if (meta == null) return "";
        JSONObject value = meta.optJSONObject(key);
        return value == null ? "" : value.optString("value", "");
    }

    private static String mediaTypeFromMime(String mime) {
        if (mime == null) return "other";
        if (mime.startsWith("image/")) return "image";
        if (mime.startsWith("audio/")) return "audio";
        if (mime.startsWith("video/")) return "video";
        return "other";
    }

    private static String encode(String value) throws IOException {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
        } catch (Exception error) {
            throw new IOException("搜索词编码失败", error);
        }
    }

    private static String httpGet(String url) throws IOException { return httpGet(url, null, null); }
    private static String httpGet(String url, String header, String value) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("User-Agent", "GameNarrator-Android/0.54");
        connection.setRequestProperty("Accept", "application/json");
        if (header != null && value != null) connection.setRequestProperty(header, value.trim());
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
            if (code >= 400) {
                throw new IOException("公共素材服务返回 " + code + (body.length() > 0 ? "：" + body : ""));
            }
            return body.toString();
        } finally {
            connection.disconnect();
        }
    }
    private static int bounded(int limit) { return Math.max(3, Math.min(limit, 50)); }
    private static void requireKey(String key, String provider) {
        if (key == null || key.isBlank()) throw new IllegalStateException("请先配置 " + provider + " API Key");
    }
}
