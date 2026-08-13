package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import android.os.Environment;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.UUID;

/** Cancellable downloader with bounded size, partial-file cleanup and atomic completion. */
public final class PublicAssetDownloader {
    public interface Progress { void update(int percent, long done, long total); }
    public static final class Result {
        private final File file;
        private final String mimeType;
        Result(File file, String mimeType) { this.file = file; this.mimeType = mimeType; }
        public File file() { return file; }
        public String mimeType() { return mimeType; }
    }
    private static final long MAX_BYTES = 512L * 1024 * 1024;
    private PublicAssetDownloader() { }

    public static Result download(Context context, PublicAsset asset, Progress progress) throws Exception {
        URL url = new URL(asset.directUrl());
        if (!("https".equalsIgnoreCase(url.getProtocol()) || "http".equalsIgnoreCase(url.getProtocol()))
                || url.getUserInfo() != null) throw new IOException("仅允许无内嵌凭据的 HTTP/HTTPS 下载地址");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15_000); connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "GameNarrator-Android/" + BuildConfig.VERSION_NAME);
        File partial = null;
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("素材下载服务返回 " + code);
            long total = connection.getContentLengthLong();
            if (total > MAX_BYTES) throw new IOException("素材超过 512 MB 安全上限");
            String mime = connection.getContentType();
            if (mime == null || !(mime.startsWith("image/") || mime.startsWith("audio/") || mime.startsWith("video/"))) {
                mime = mediaMime(asset.mediaType());
            }
            File base = context.getExternalFilesDir(directory(asset.mediaType()));
            if (base == null) base = new File(context.getFilesDir(), "public-assets");
            File folder = new File(base, "public-assets");
            if (!folder.exists() && !folder.mkdirs()) throw new IOException("无法创建公共素材目录");
            String suffix = suffix(mime);
            File completed = new File(folder, safeName(asset.title()) + "-" + UUID.randomUUID() + suffix);
            partial = new File(completed.getAbsolutePath() + ".part");
            long done = 0;
            try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(partial)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("素材下载已取消");
                    done += read;
                    if (done > MAX_BYTES) throw new IOException("素材超过 512 MB 安全上限");
                    out.write(buffer, 0, read);
                    if (progress != null) progress.update(total > 0 ? (int) Math.min(100, done * 100 / total) : -1, done, total);
                }
                out.getFD().sync();
            }
            if (!partial.renameTo(completed)) throw new IOException("素材文件原子落盘失败");
            return new Result(completed, mime);
        } catch (Exception error) {
            PublicAssetDownloadTransaction.rollbackPartial(partial);
            throw error;
        } finally { connection.disconnect(); }
    }

    private static String directory(String type) {
        if ("audio".equals(type)) return Environment.DIRECTORY_MUSIC;
        if ("video".equals(type)) return Environment.DIRECTORY_MOVIES;
        return Environment.DIRECTORY_PICTURES;
    }
    private static String mediaMime(String type) { return (type == null ? "other" : type) + "/*"; }
    private static String suffix(String mime) {
        String value = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        if (value.contains("png")) return ".png"; if (value.contains("webp")) return ".webp";
        if (value.contains("jpeg")) return ".jpg"; if (value.contains("wav")) return ".wav";
        if (value.contains("mpeg")) return value.startsWith("audio/") ? ".mp3" : ".mp4";
        if (value.contains("mp4")) return ".mp4"; return ".bin";
    }
    private static String safeName(String name) {
        String value = name == null ? "public-asset" : name.replaceAll("[^\\p{L}\\p{N}._-]+", "-");
        return value.isBlank() ? "public-asset" : value.substring(0, Math.min(60, value.length()));
    }
}
