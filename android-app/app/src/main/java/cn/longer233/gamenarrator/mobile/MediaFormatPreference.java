package cn.longer233.gamenarrator.mobile;

import java.util.Locale;

public final class MediaFormatPreference {
    public static final String AUTO = "AUTO";
    public static final String MP4 = "MP4";
    public static final String MOV = "MOV";
    public static final String WEBM = "WEBM";
    public static final String MKV = "MKV";
    public static final String AUDIO = "AUDIO";

    private MediaFormatPreference() { }

    public static boolean matches(String url, String mimeType, String preference) {
        String format = preference == null ? AUTO : preference.toUpperCase(Locale.ROOT);
        if (AUTO.equals(format)) return true;
        String mime = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        String path = url == null ? "" : url.toLowerCase(Locale.ROOT);
        switch (format) {
            case MP4: return mime.contains("mp4") || mime.contains("m4v") || path.matches(".*\\.(mp4|m4v)(\\?.*)?");
            case MOV: return mime.contains("quicktime") || path.matches(".*\\.mov(\\?.*)?");
            case WEBM: return mime.contains("webm") || path.matches(".*\\.webm(\\?.*)?");
            case MKV: return mime.contains("matroska") || path.matches(".*\\.mkv(\\?.*)?");
            case AUDIO: return mime.startsWith("audio/") || path.matches(".*\\.(mp3|m4a|aac|wav|ogg|opus)(\\?.*)?");
            default: return true;
        }
    }

    public static String mimeFor(String url) {
        String path = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (path.matches(".*\\.(mp4|m4v)(\\?.*)?")) return "video/mp4";
        if (path.matches(".*\\.mov(\\?.*)?")) return "video/quicktime";
        if (path.matches(".*\\.webm(\\?.*)?")) return "video/webm";
        if (path.matches(".*\\.mkv(\\?.*)?")) return "video/x-matroska";
        if (path.matches(".*\\.(mp3|m4a|aac|wav|ogg|opus)(\\?.*)?")) return "audio/x-generic";
        return "";
    }
}
