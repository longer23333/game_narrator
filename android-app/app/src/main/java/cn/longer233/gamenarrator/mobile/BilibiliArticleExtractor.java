package cn.longer233.gamenarrator.mobile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Extracts Bilibili read/cv and opus content from public endpoints. Restricted
 * content can pass an in-memory session cookie; cookies are never persisted.
 */
public final class BilibiliArticleExtractor {
    private static final Pattern CV_ID = Pattern.compile("read/cv(\\d+)");
    private static final Pattern OPUS_ID = Pattern.compile("opus/(\\d+)");

    private BilibiliArticleExtractor() { }

    public static BilibiliArticle extract(String url, String cookie) throws IOException {
        String id = articleId(url);
        if (id == null) throw new IllegalArgumentException("仅支持 bilibili.com/read/cv... 或 opus/... 链接");
        boolean opus = url.contains("opus/");
        String api = opus
                ? "https://api.bilibili.com/x/polymer/web-dynamic/v1/detail?id=" + id + "&type=opus"
                : "https://api.bilibili.com/x/article/view?id=" + id;
        String json = httpGet(api, cookie);
        try {
            return opus ? parseOpus(json, url) : parseArticle(json, url);
        } catch (JSONException error) {
            throw new IOException("Bilibili 返回了无法解析的结果（受限内容可能需要登录会话）", error);
        }
    }

    static String articleId(String url) {
        if (url == null) return null;
        Matcher cv = CV_ID.matcher(url);
        if (cv.find()) return cv.group(1);
        Matcher opus = OPUS_ID.matcher(url);
        if (opus.find()) return opus.group(1);
        return null;
    }

    static BilibiliArticle parseArticle(String json, String sourceUrl) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONObject data = root.optJSONObject("data");
        String title = data == null ? "" : data.optString("title", "");
        String content = data == null ? "" : cleanHtml(data.optString("content", ""));
        List<String> images = new ArrayList<>();
        if (data != null) {
            addStrings(data.optJSONArray("origin_image_urls"), images);
            addStrings(data.optJSONArray("image_urls"), images);
        }
        return new BilibiliArticle(title, content, sourceUrl, images);
    }

    static BilibiliArticle parseOpus(String json, String sourceUrl) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONObject data = root.optJSONObject("data");
        JSONObject item = data == null ? null : data.optJSONObject("item");
        String title = "";
        String content = "";
        List<String> images = new ArrayList<>();
        if (item != null) {
            title = item.optString("title", "");
            content = cleanHtml(item.optString("content", ""));
            JSONArray imageArray = item.optJSONArray("images");
            if (imageArray != null) {
                for (int i = 0; i < imageArray.length(); i++) {
                    Object value = imageArray.opt(i);
                    if (value instanceof String) images.add((String) value);
                    else if (value instanceof JSONObject) {
                        String url = ((JSONObject) value).optString("url", "");
                        if (!url.isBlank()) images.add(url);
                    }
                }
            }
        }
        if (content.isBlank()) content = cleanHtml(data == null ? "" : data.optString("content", ""));
        return new BilibiliArticle(title, content, sourceUrl, images);
    }

    private static void addStrings(JSONArray array, List<String> out) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "");
            if (!value.isBlank()) out.add(value);
        }
    }

    private static String cleanHtml(String html) {
        if (html == null) return "";
        String text = html.replaceAll("<[^>]*>", " ");
        text = text.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"");
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String httpGet(String url, String cookie) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 GameNarrator-Android/0.54");
        connection.setRequestProperty("Accept", "application/json");
        if (cookie != null && !cookie.isBlank()) connection.setRequestProperty("Cookie", cookie);
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
            if (code >= 400) throw new IOException("Bilibili 返回 " + code);
            return body.toString();
        } finally {
            connection.disconnect();
        }
    }
}
