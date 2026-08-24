package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import android.os.Environment;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class ComputerCompanionClient {
    static final class Variant {
        private final String id,label,extension; private final Long bytes;
        Variant(String id,String label,String extension,Long bytes){this.id=id;this.label=label;this.extension=extension;this.bytes=bytes;}
        String id(){return id;} String label(){return label;} String extension(){return extension;} Long bytes(){return bytes;}
    }
    static final class Media {
        private final String sourceUrl,title,creator,thumbnail; private final Double duration;
        private final List<String> tags; private final List<Variant> variants;
        Media(String sourceUrl,String title,String creator,String thumbnail,Double duration,List<String> tags,List<Variant> variants){this.sourceUrl=sourceUrl;this.title=title;this.creator=creator;this.thumbnail=thumbnail;this.duration=duration;this.tags=tags;this.variants=variants;}
        String sourceUrl(){return sourceUrl;} String title(){return title;} String creator(){return creator;} String thumbnail(){return thumbnail;}
        Double duration(){return duration;} List<String> tags(){return tags;} List<Variant> variants(){return variants;}
    }

    private final URI base;
    private final String token;
    private String sessionCookie;

    ComputerCompanionClient(String address, String token) throws Exception {
        this.base = validateAddress(address);
        this.token = token == null ? "" : token.trim();
        if (this.token.length() < 24) throw new IllegalArgumentException("配对密钥至少需要 24 位");
    }

    Media resolve(String sourceUrl) throws Exception {
        JSONObject response = exchange("POST", "/api/media-import/resolve",
                new JSONObject().put("url", sourceUrl).put("rightsConfirmed", true));
        List<Variant> variants = new ArrayList<>();
        JSONArray values = response.optJSONArray("variants");
        if (values != null) for (int index = 0; index < values.length(); index++) {
            JSONObject item = values.getJSONObject(index);
            variants.add(new Variant(item.getString("formatId"), item.optString("label", "自动"),
                    item.optString("extension", "mp4"), item.has("approximateBytes") && !item.isNull("approximateBytes")
                    ? item.getLong("approximateBytes") : null));
        }
        if (variants.isEmpty()) variants.add(new Variant("best", "自动选择最佳画质", "mp4", null));
        List<String> tags = new ArrayList<>();
        JSONArray tagValues = response.optJSONArray("tags");
        if (tagValues != null) for (int index = 0; index < tagValues.length(); index++) tags.add(tagValues.optString(index));
        return new Media(sourceUrl, response.optString("title", "平台视频"), response.optString("creator", ""),
                response.optString("thumbnail", ""), response.has("durationSeconds") && !response.isNull("durationSeconds")
                ? response.getDouble("durationSeconds") : null, tags, variants);
    }

    RemoteMediaImporter.Result download(Context context, Media media, Variant variant,
                                        RemoteMediaImporter.Progress progress) throws Exception {
        JSONObject request = new JSONObject().put("url", media.sourceUrl()).put("formatId", variant.id())
                .put("subtitles", false).put("addToLibrary", false).put("rightsConfirmed", true)
                .put("title", media.title()).put("creator", media.creator()).put("thumbnail", media.thumbnail())
                .put("tags", new JSONArray(media.tags()));
        if (media.duration() != null) request.put("durationSeconds", media.duration());
        String id = exchange("POST", "/api/media-import/download-jobs", request).getString("id");
        String downloadUrl;
        while (true) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("电脑协同下载已取消");
            JSONObject job = exchange("GET", "/api/media-import/download-jobs/" + id, null);
            String status = job.optString("status");
            if ("FAILED".equals(status)) throw new IllegalStateException(job.optString("error", "电脑端下载失败"));
            if ("COMPLETED".equals(status)) { downloadUrl = job.getJSONObject("result").getString("downloadUrl"); break; }
            JSONObject state = job.optJSONObject("progress");
            progress.update(state == null ? -1 : percent(state.optString("percent")), 0, -1);
            Thread.sleep(750);
        }
        File root = new File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "imports");
        if (!root.isDirectory() && !root.mkdirs()) throw new IllegalStateException("无法创建导入目录");
        HttpURLConnection connection = open(downloadUrl);
        connection.connect();
        if (connection.getResponseCode() != 200) throw new IllegalStateException("电脑端文件下载失败：HTTP " + connection.getResponseCode());
        String name = "companion-" + System.currentTimeMillis() + ".mp4";
        File output = new File(root, name), partial = new File(root, name + ".part");
        long total = connection.getContentLengthLong(), done = 0;
        try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream()); FileOutputStream out = new FileOutputStream(partial)) {
            byte[] buffer = new byte[64 * 1024]; int read;
            while ((read = input.read(buffer)) >= 0) { if (Thread.currentThread().isInterrupted()) throw new InterruptedException(); out.write(buffer, 0, read); done += read; progress.update(total > 0 ? (int)(done * 100 / total) : -1, done, total); }
            out.getFD().sync();
        } finally { connection.disconnect(); }
        RemoteMediaImporter.finalizeDownload(partial, output);
        progress.update(100, done, total);
        return new RemoteMediaImporter.Result(output, "video/mp4", output.getName());
    }

    private JSONObject exchange(String method, String path, JSONObject body) throws Exception {
        HttpURLConnection connection = open(path);
        connection.setRequestMethod(method);
        if (body != null) { connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "application/json; charset=utf-8"); connection.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        int status = connection.getResponseCode(); rememberSession(connection);
        java.io.InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        String text = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        connection.disconnect();
        if (status < 200 || status >= 300) throw new IllegalStateException("电脑协同请求失败（HTTP " + status + "）" + (text.isBlank() ? "" : "：" + text));
        return new JSONObject(text);
    }

    private HttpURLConnection open(String path) throws Exception {
        URI target = path.startsWith("http") ? URI.create(path) : base.resolve(path);
        HttpURLConnection connection = (HttpURLConnection) new URL(target.toString()).openConnection();
        connection.setConnectTimeout(10_000); connection.setReadTimeout(30_000);
        connection.setRequestProperty("X-GameNarrator-Companion", token);
        if (sessionCookie != null) connection.setRequestProperty("Cookie", sessionCookie);
        return connection;
    }

    private void rememberSession(HttpURLConnection connection) {
        String value = connection.getHeaderField("Set-Cookie");
        if (value != null && !value.isBlank()) sessionCookie = value.split(";", 2)[0];
    }

    static URI validateAddress(String value) throws Exception {
        URI uri = URI.create(value == null ? "" : value.trim());
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null)
            throw new IllegalArgumentException("电脑地址必须是 HTTP/HTTPS 地址");
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) if (!(address.isLoopbackAddress() || address.isSiteLocalAddress()))
            throw new IllegalArgumentException("电脑协同只允许局域网或本机地址");
        String normalized = uri.toString(); if (!normalized.endsWith("/")) normalized += "/";
        return URI.create(normalized);
    }

    private static int percent(String value) { try { return (int)Double.parseDouble(value.replace("%", "").trim()); } catch (Exception ignored) { return -1; } }
}
