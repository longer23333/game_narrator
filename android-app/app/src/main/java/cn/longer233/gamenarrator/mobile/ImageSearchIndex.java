package cn.longer233.gamenarrator.mobile;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory cosine-similarity index for MobileNet feature vectors.
 */
public final class ImageSearchIndex {
    private final Map<String, float[]> vectors = new HashMap<>();

    public void add(String key, float[] vector) {
        vectors.put(key, normalize(vector));
    }

    public void remove(String key) {
        vectors.remove(key);
    }

    public int size() {
        return vectors.size();
    }

    public List<String> search(float[] query, int limit) {
        float[] normalized = normalize(query);
        List<Map.Entry<String, Float>> scored = new ArrayList<>();
        for (Map.Entry<String, float[]> entry : vectors.entrySet()) {
            scored.add(new AbstractMap.SimpleEntry<>(entry.getKey(), cosine(normalized, entry.getValue())));
        }
        scored.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, scored.size()); i++) out.add(scored.get(i).getKey());
        return out;
    }

    private static float cosine(float[] a, float[] b) {
        float dot = 0;
        int length = Math.min(a.length, b.length);
        for (int i = 0; i < length; i++) dot += a[i] * b[i];
        return dot;
    }

    private static float[] normalize(float[] vector) {
        float[] out = new float[vector == null ? 0 : vector.length];
        if (vector == null || vector.length == 0) return out;
        double sum = 0;
        for (float value : vector) sum += value * value;
        double norm = Math.sqrt(sum);
        if (norm == 0) return out;
        for (int i = 0; i < vector.length; i++) out[i] = (float) (vector[i] / norm);
        return out;
    }
}
