package cn.longer233.gamenarrator.mobile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Strict parser for the 3D subset of the Adobe .cube interchange format. */
public final class CubeLutParser {
    public static final int MAX_SIZE = 65;
    private CubeLutParser() { }

    public static Parsed parse(Reader source) throws IOException {
        int size = 0;
        float[] minimum = {0, 0, 0};
        float[] maximum = {1, 1, 1};
        List<float[]> values = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(source)) {
            String raw;
            while ((raw = reader.readLine()) != null) {
                String line = raw.replaceFirst("#.*$", "").trim();
                if (line.isEmpty() || line.toUpperCase(Locale.ROOT).startsWith("TITLE")) continue;
                String[] parts = line.split("\\s+");
                String directive = parts[0].toUpperCase(Locale.ROOT);
                if ("LUT_1D_SIZE".equals(directive)) throw new IllegalArgumentException("暂不支持 1D LUT，请选择 3D .cube 文件");
                if ("LUT_3D_SIZE".equals(directive)) {
                    if (parts.length != 2) throw new IllegalArgumentException("LUT_3D_SIZE 格式无效");
                    size = Integer.parseInt(parts[1]);
                    if (size < 2 || size > MAX_SIZE) throw new IllegalArgumentException("3D LUT 边长必须为 2–" + MAX_SIZE);
                } else if ("DOMAIN_MIN".equals(directive)) minimum = triple(parts, "DOMAIN_MIN");
                else if ("DOMAIN_MAX".equals(directive)) maximum = triple(parts, "DOMAIN_MAX");
                else values.add(triple(parts, "颜色数据"));
            }
        }
        if (size == 0) throw new IllegalArgumentException("缺少 LUT_3D_SIZE");
        int expected = size * size * size;
        if (values.size() != expected) throw new IllegalArgumentException("颜色数据数量应为 " + expected + "，实际为 " + values.size());
        for (int channel = 0; channel < 3; channel++)
            if (!(maximum[channel] > minimum[channel])) throw new IllegalArgumentException("DOMAIN_MAX 必须大于 DOMAIN_MIN");
        int[][][] cube = new int[size][size][size];
        for (int index = 0; index < values.size(); index++) {
            // .cube lists red fastest, then green, then blue. Media3 indexes [red][green][blue].
            int red = index % size;
            int green = (index / size) % size;
            int blue = index / (size * size);
            float[] value = values.get(index);
            cube[red][green][blue] = 0xff000000 | (toByte(value[0]) << 16) | (toByte(value[1]) << 8) | toByte(value[2]);
        }
        return new Parsed(size, cube, minimum, maximum);
    }

    private static float[] triple(String[] parts, String label) {
        if (parts.length != 3 && parts.length != 4) throw new IllegalArgumentException(label + " 需要三个数值");
        int offset = parts.length == 4 ? 1 : 0;
        float[] values = new float[3];
        for (int i = 0; i < 3; i++) {
            values[i] = Float.parseFloat(parts[i + offset]);
            if (!Float.isFinite(values[i])) throw new IllegalArgumentException(label + " 包含非有限数值");
        }
        return values;
    }

    private static int toByte(float value) { return Math.round(Math.max(0, Math.min(1, value)) * 255); }
    public static final class Parsed {
        private final int size;
        private final int[][][] cube;
        private final float[] domainMinimum;
        private final float[] domainMaximum;
        Parsed(int size, int[][][] cube, float[] domainMinimum, float[] domainMaximum) {
            this.size = size;
            this.cube = cube;
            this.domainMinimum = domainMinimum.clone();
            this.domainMaximum = domainMaximum.clone();
        }
        public int size() { return size; }
        public int[][][] cube() { return cube; }
        public float[] domainMinimum() { return domainMinimum.clone(); }
        public float[] domainMaximum() { return domainMaximum.clone(); }
    }
}
