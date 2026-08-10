package cn.longer233.gamenarrator.mobile;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import java.lang.reflect.Field;
import java.util.Locale;

public final class MobileCodecProbe {
    private MobileCodecProbe() { }

    public static boolean hasEncoder(String mime) {
        if (mime == null) return false;
        for (MediaCodecInfo codec : new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()) {
            if (!codec.isEncoder()) continue;
            for (String type : codec.getSupportedTypes()) {
                if (mime.equalsIgnoreCase(type)) return true;
            }
        }
        return false;
    }

    public static boolean proResSupported() {
        if (hasEncoder("video/x-prores")) return true;
        for (MediaCodecInfo codec : new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()) {
            if (!codec.isEncoder()) continue;
            for (String type : codec.getSupportedTypes()) {
                if (type != null && type.toLowerCase(Locale.ROOT).contains("prores")) return true;
            }
        }
        return false;
    }

    public static boolean movMuxerSupported() {
        try {
            for (Field field : android.media.MediaMuxer.OutputFormat.class.getFields()) {
                String name = field.getName().toUpperCase(Locale.ROOT);
                if (name.contains("MOV") || name.contains("QUICKTIME")) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }
}
