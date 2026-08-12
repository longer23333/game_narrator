package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OnnxTensor;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * ONNX Runtime adapter for bundled or user-installed vision/text models. The
 * model directory exposes BUNDLED, USER_INSTALLED and MISSING explicitly;
 * missing models produce a clear error instead of overclaiming availability.
 */
public final class OnnxModelRunner {
    private static final Map<String, CachedSession> SESSION_CACHE = new HashMap<>();

    private OnnxModelRunner() { }

    private static final class CachedSession {
        private final String stamp;
        private final OrtSession session;

        private CachedSession(String stamp, OrtSession session) {
            this.stamp = stamp;
            this.session = session;
        }
    }

    public static File modelFile(Context context, String kind) {
        return modelFile(MobileModelDirectory.modelsDir(context), kind);
    }

    static File modelFile(File dir, String kind) {
        return MobileModelDirectory.modelFile(dir, kind);
    }

    public static OrtSession openChecked(Context context, String kind) {
        return openChecked(MobileModelDirectory.modelsDir(context), kind);
    }

    /**
     * Reuses one optimized session per model kind. A replaced model invalidates
     * and closes the previous session before the new file is loaded.
     */
    public static synchronized OrtSession cachedChecked(Context context, String kind) {
        File model = requireModel(MobileModelDirectory.modelsDir(context), kind);
        String stamp = model.getAbsolutePath() + ':' + model.length() + ':' + model.lastModified();
        CachedSession cached = SESSION_CACHE.get(kind);
        if (cached != null && cached.stamp.equals(stamp)) return cached.session;
        if (cached != null) close(cached.session);
        OrtSession session = createSession(model);
        SESSION_CACHE.put(kind, new CachedSession(stamp, session));
        return session;
    }

    static OrtSession openChecked(File dir, String kind) {
        return createSession(requireModel(dir, kind));
    }

    private static File requireModel(File dir, String kind) {
        File model = modelFile(dir, kind);
        if (model == null) throw new IllegalStateException(
                "未检测到 " + kind + "-*.onnx，请先用 install-models.ps1 放入模型目录");
        return model;
    }

    private static OrtSession createSession(File model) {
        try {
            int threads = Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() / 2));
            try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
                options.setIntraOpNumThreads(threads);
                options.setInterOpNumThreads(1);
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
                options.setMemoryPatternOptimization(true);
                options.setCPUArenaAllocator(true);
                return OrtEnvironment.getEnvironment().createSession(model.getAbsolutePath(), options);
            }
        } catch (Exception error) {
            throw new IllegalStateException("模型加载失败：" + (error.getMessage() == null ? "未知错误" : error.getMessage()), error);
        }
    }

    public static synchronized void clearCache() {
        SESSION_CACHE.values().forEach(entry -> close(entry.session));
        SESSION_CACHE.clear();
    }

    public static void closeTensors(Map<String, OnnxTensor> tensors) {
        if (tensors == null) return;
        tensors.values().forEach(tensor -> {
            try { tensor.close(); } catch (Exception ignored) { }
        });
    }

    public static void close(OrtSession session) {
        if (session != null) {
            try { session.close(); } catch (Exception ignored) { }
        }
    }
}
