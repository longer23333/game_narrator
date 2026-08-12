package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Local sentence embedding with the bundled bge-small-zh ONNX model. Enables
 * real semantic shot search on-device; when the model is absent the UI keeps
 * showing keyword search and never claims semantic capability.
 */
public final class TextEmbedding {
    private static final int MAX_LENGTH = 64;
    private static final int CLS = 101;
    private static final int SEP = 102;
    private static final int UNK = 100;
    private static final int PAD = 0;

    private static volatile Map<String, Integer> vocab;

    private TextEmbedding() { }

    public static boolean isReady(Context context) {
        File dir = MobileModelDirectory.modelsDir(context);
        return OnnxModelRunner.modelFile(dir, "embedding") != null
                && new File(dir, "text-vocab.txt").isFile();
    }

    public static float[] encode(Context context, String text) throws Exception {
        if (!isReady(context)) throw new IllegalStateException("未检测到本地语义模型（text-embedding.onnx）");
        Map<String, Integer> table = vocab(context);
        List<Integer> ids = new ArrayList<>();
        ids.add(CLS);
        for (String token : wordpiece(text, table)) {
            if (ids.size() >= MAX_LENGTH - 1) break;
            Integer id = table.get(token);
            ids.add(id == null ? UNK : id);
        }
        ids.add(SEP);
        while (ids.size() < MAX_LENGTH) ids.add(PAD);

        long[] inputIds = new long[MAX_LENGTH];
        long[] mask = new long[MAX_LENGTH];
        long[] types = new long[MAX_LENGTH];
        for (int i = 0; i < MAX_LENGTH; i++) {
            inputIds[i] = ids.get(i);
            mask[i] = i < ids.size() && ids.get(i) != PAD ? 1 : 0;
        }

        OrtSession session = session(context);
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        Map<String, OnnxTensor> inputs = new HashMap<>();
        for (String name : session.getInputNames()) {
            String lower = name.toLowerCase();
            if (lower.contains("token_type") || lower.contains("segment")) {
                inputs.put(name, OnnxTensor.createTensor(env, new long[][]{types}));
            } else if (lower.contains("attention")) {
                inputs.put(name, OnnxTensor.createTensor(env, new long[][]{mask}));
            } else {
                inputs.put(name, OnnxTensor.createTensor(env, new long[][]{inputIds}));
            }
        }
        try {
            OrtSession.Result result = session.run(inputs);
            try {
                float[] vector = null;
                for (Map.Entry<String, ai.onnxruntime.OnnxValue> entry : result) {
                    String name = entry.getKey().toLowerCase();
                    Object value = ((OnnxTensor) entry.getValue()).getValue();
                    if ((name.contains("sentence") || name.contains("dense") || name.contains("pooler"))
                            && value instanceof float[][]) {
                        float[][] batch = (float[][]) value;
                        if (batch.length > 0) vector = batch[0];
                    } else if ((name.contains("last_hidden") || name.contains("hidden"))
                            && value instanceof float[][][]) {
                        float[][][] batch = (float[][][]) value;
                        if (batch.length > 0 && batch[0].length > 0) vector = batch[0][0];
                    }
                }
                if (vector == null || vector.length == 0) throw new IllegalStateException("模型输出中未找到句向量");
                return normalize(vector);
            } finally {
                result.close();
            }
        } finally {
            OnnxModelRunner.closeTensors(inputs);
        }
    }

    private static List<String> wordpiece(String text, Map<String, Integer> table) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            out.add("[UNK]");
            return out;
        }
        for (String raw : normalized.split(" ")) {
            if (raw.isBlank()) continue;
            int start = 0;
            while (start < raw.length()) {
                char c = raw.charAt(start);
                if (isCjk(c)) {
                    out.add(String.valueOf(c));
                    start++;
                    continue;
                }
                StringBuilder best = new StringBuilder();
                for (int end = raw.length(); end > start; end--) {
                    String piece = raw.substring(start, end);
                    if (table.containsKey(piece) && piece.length() > best.length()) {
                        best.setLength(0);
                        best.append(piece);
                    }
                    String sub = "##" + piece;
                    if (table.containsKey(sub) && sub.length() > best.length()) {
                        best.setLength(0);
                        best.append(sub);
                    }
                }
                if (best.length() == 0) {
                    out.add("[UNK]");
                    start++;
                } else {
                    out.add(best.toString());
                    start += best.toString().startsWith("##") ? best.length() - 2 : best.length();
                }
            }
        }
        return out;
    }

    private static boolean isCjk(char c) {
        return (c >= '\u4E00' && c <= '\u9FFF') || (c >= '\u3400' && c <= '\u4DBF');
    }

    private static Map<String, Integer> vocab(Context context) throws Exception {
        Map<String, Integer> local = vocab;
        if (local != null) return local;
        synchronized (TextEmbedding.class) {
            if (vocab != null) return vocab;
            File file = new File(MobileModelDirectory.modelsDir(context), "text-vocab.txt");
            Map<String, Integer> table = new HashMap<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                int id = 0;
                while ((line = reader.readLine()) != null) table.put(line.trim(), id++);
            }
            vocab = table;
            return table;
        }
    }

    private static OrtSession session(Context context) throws Exception {
        return OnnxModelRunner.cachedChecked(context, "embedding");
    }

    private static float[] normalize(float[] vector) {
        double sum = 0;
        for (float value : vector) sum += value * value;
        double norm = Math.sqrt(sum);
        if (norm == 0) return vector;
        float[] out = new float[vector.length];
        for (int i = 0; i < vector.length; i++) out[i] = (float) (vector[i] / norm);
        return out;
    }
}
