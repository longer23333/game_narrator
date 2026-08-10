package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Resolves a timeline clip Uri into a local file FFmpeg can read. Content Uris
 * are copied once into the cache because ffmpeg runs as a child process.
 */
public final class MediaInputFile {
    private MediaInputFile() { }

    public static File resolve(Context context, Uri uri) throws Exception {
        if (uri != null && "file".equals(uri.getScheme())) return new File(uri.getPath());
        File out = new File(context.getCacheDir(), "clip-" + System.currentTimeMillis() + ".mp4");
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(out)) {
            if (in == null) throw new IllegalStateException("无法读取片段");
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) != -1) os.write(buffer, 0, read);
        }
        return out;
    }
}
