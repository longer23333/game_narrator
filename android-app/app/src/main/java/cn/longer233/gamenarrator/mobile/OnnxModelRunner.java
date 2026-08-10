package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import java.io.File;

/**
 * ONNX Runtime adapter for user-provided vision/text models. Models are never
 * bundled; when absent the runner reports a clear missing-model message instead
 * of pretending the capability is available.
 */
public final class OnnxModelRunner {
    private OnnxModelRunner() { }

    public static File modelFile(Context context, String kind) {
        return modelFile(MobileModelDirectory.modelsDir(context), kind);
    }

    static File modelFile(File dir, String kind) {
        if ("embedding".equals(kind)) {
            File[] files = dir.listFiles((unused, name) -> name.startsWith("text-") && name.endsWith(".onnx")
                    && name.contains("embedding"));
            return files != null && files.length > 0 ? files[0] : null;
        }
        if ("text".equals(kind)) {
            File[] files = dir.listFiles((unused, name) -> name.startsWith("text-") && name.endsWith(".onnx")
                    && !name.contains("embedding"));
            return files != null && files.length > 0 ? files[0] : null;
        }
        String prefix = "vision".equals(kind) ? "vision-" : "text-";
        File[] files = dir.listFiles((unused, name) -> name.startsWith(prefix) && name.endsWith(".onnx"));
        return files != null && files.length > 0 ? files[0] : null;
    }

    public static OrtSession openChecked(Context context, String kind) {
        return openChecked(MobileModelDirectory.modelsDir(context), kind);
    }

    static OrtSession openChecked(File dir, String kind) {
        File model = modelFile(dir, kind);
        if (model == null) {
            throw new IllegalStateException("未检测到 " + kind + "-*.onnx，请先用 install-models.ps1 放入模型目录");
        }
        try {
            return OrtEnvironment.getEnvironment().createSession(model.getAbsolutePath(),
                    new OrtSession.SessionOptions());
        } catch (Exception error) {
            throw new IllegalStateException("模型加载失败：" + (error.getMessage() == null ? "未知错误" : error.getMessage()), error);
        }
    }

    public static void close(OrtSession session) {
        if (session != null) {
            try { session.close(); } catch (Exception ignored) { }
        }
    }
}
