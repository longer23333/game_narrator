package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Optional first-run installer for models bundled under assets/models/.
 * Files matching whisper-*.bin / vision-*.onnx / text-*.onnx plus the
 * whisper-cli engine are copied once into the external models directory so the
 * existing scanner and ONNX runner can consume them without a separate push step.
 */
public final class MobileModelBundler {
    private static final String ASSET_DIR = "models";

    private MobileModelBundler() { }

    public static int ensureBundled(Context context) throws IOException {
        return ensureBundled(context.getAssets(), MobileModelDirectory.modelsDir(context));
    }

    public static int ensureBundled(AssetManager assets, File targetDir) throws IOException {
        String[] names = assets.list(ASSET_DIR);
        if (names == null || names.length == 0) return 0;
        if (!targetDir.isDirectory() && !targetDir.mkdirs()) return 0;
        int copied = 0;
        for (String name : names) {
            if (!isModelName(name)) continue;
            File target = new File(targetDir, name);
            long assetLength = -1;
            try {
                assetLength = assets.openFd(ASSET_DIR + "/" + name).getLength();
            } catch (IOException ignored) { }
            if (target.isFile() && assetLength > 0 && target.length() == assetLength) continue;
            try (InputStream in = assets.open(ASSET_DIR + "/" + name);
                 OutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
            copied++;
        }
        return copied;
    }

    private static boolean isModelName(String name) {
        return (name.startsWith("whisper-") && name.endsWith(".bin"))
                || (name.startsWith("vision-") && name.endsWith(".onnx"))
                || (name.startsWith("text-") && (name.endsWith(".onnx") || name.endsWith(".json") || name.endsWith(".txt")))
                || "whisper-cli".equals(name)
                || "whisper-cli.exe".equals(name)
                || "ffmpeg".equals(name)
                || "tokenizer.json".equals(name)
                || "vocab.json".equals(name)
                || "merges.txt".equals(name)
                || "config.json".equals(name)
                || "tokenizer_config.json".equals(name)
                || "generation_config.json".equals(name)
                || "special_tokens_map.json".equals(name);
    }
}
