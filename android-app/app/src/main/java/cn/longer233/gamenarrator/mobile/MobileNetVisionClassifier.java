package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import android.graphics.Bitmap;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONArray;

/**
 * MobileNetV2 image classifier over ONNX Runtime. Uses the bundled
 * vision-mobilenetv2.onnx and ImageNet labels from assets/models/.
 */
public final class MobileNetVisionClassifier {
    private static final int SIZE = 224;
    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};

    private MobileNetVisionClassifier() { }

    public static List<String> classify(Context context, Bitmap source) throws Exception {
        float[] probabilities = probabilities(context, source);
        List<String> labels = readLabels(context);
        List<Integer> top = topIndices(probabilities, 5);
        List<String> out = new ArrayList<>();
        for (int i : top) {
            String name = i < labels.size() ? labels.get(i) : ("class " + i);
            out.add(name + " (" + Math.round(probabilities[i] * 100) + "%)");
        }
        return out;
    }

    public static float[] embed(Context context, Bitmap source) throws Exception {
        return probabilities(context, source);
    }

    private static float[] probabilities(Context context, Bitmap source) throws Exception {
        if (source == null) throw new IllegalStateException("无法读取画面");
        Bitmap resized = centerCropScale(source, SIZE, SIZE);
        float[] input = preprocess(resized);
        OrtSession session = OnnxModelRunner.cachedChecked(context, "vision");
        Map<String, OnnxTensor> inputs = new HashMap<>();
        try {
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            float[][][][] tensor = new float[1][3][SIZE][SIZE];
            int index = 0;
            for (int c = 0; c < 3; c++) {
                for (int y = 0; y < SIZE; y++) {
                    for (int x = 0; x < SIZE; x++) {
                        tensor[0][c][y][x] = input[index++];
                    }
                }
            }
            for (String name : session.getInputNames()) {
                inputs.put(name, OnnxTensor.createTensor(env, tensor));
            }
            OrtSession.Result result = session.run(inputs);
            try {
                float[] logits = null;
                for (Map.Entry<String, ai.onnxruntime.OnnxValue> entry : result) {
                    Object value = ((OnnxTensor) entry.getValue()).getValue();
                    if (value instanceof float[][]) {
                        float[][] batch = (float[][]) value;
                        if (batch.length == 1) logits = batch[0];
                    }
                }
                if (logits == null) throw new IllegalStateException("未找到分类输出");
                return softmax(logits);
            } finally {
                result.close();
            }
        } finally {
            OnnxModelRunner.closeTensors(inputs);
            if (resized != source && !resized.isRecycled()) resized.recycle();
        }
    }

    private static float[] preprocess(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        float[] out = new float[3 * SIZE * SIZE];
        int index = 0;
        for (int c = 0; c < 3; c++) {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    int pixel = pixels[y * width + x];
                    int channel = c == 0 ? (pixel >> 16) & 0xFF : c == 1 ? (pixel >> 8) & 0xFF : pixel & 0xFF;
                    out[index++] = ((channel / 255f) - MEAN[c]) / STD[c];
                }
            }
        }
        return out;
    }

    private static Bitmap centerCropScale(Bitmap source, int targetW, int targetH) {
        int width = source.getWidth();
        int height = source.getHeight();
        float scale = Math.max((float) targetW / width, (float) targetH / height);
        int scaledW = Math.round(width * scale);
        int scaledH = Math.round(height * scale);
        Bitmap scaled = Bitmap.createScaledBitmap(source, scaledW, scaledH, true);
        int x = Math.max(0, (scaledW - targetW) / 2);
        int y = Math.max(0, (scaledH - targetH) / 2);
        Bitmap crop = Bitmap.createBitmap(scaled, x, y, targetW, targetH);
        if (crop != scaled) scaled.recycle();
        return crop;
    }

    private static float[] softmax(float[] values) {
        float max = Float.NEGATIVE_INFINITY;
        for (float value : values) max = Math.max(max, value);
        double sum = 0;
        for (float value : values) sum += Math.exp(value - max);
        float[] out = new float[values.length];
        for (int i = 0; i < values.length; i++) out[i] = (float) (Math.exp(values[i] - max) / sum);
        return out;
    }

    private static List<Integer> topIndices(float[] probabilities, int count) {
        List<Integer> indices = new ArrayList<>();
        for (int step = 0; step < count; step++) {
            int best = -1;
            for (int i = 0; i < probabilities.length; i++) {
                if (!indices.contains(i) && (best < 0 || probabilities[i] > probabilities[best])) best = i;
            }
            if (best < 0) break;
            indices.add(best);
        }
        return indices;
    }

    private static List<String> readLabels(Context context) throws IOException {
        List<String> labels = new ArrayList<>();
        try (InputStream input = context.getAssets().open("models/imagenet_labels.json");
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
            try {
                JSONArray array = new JSONArray(builder.toString());
                for (int i = 0; i < array.length(); i++) labels.add(array.optString(i));
            } catch (JSONException error) {
                throw new IOException("无法解析 ImageNet 标签", error);
            }
        }
        return labels;
    }
}
