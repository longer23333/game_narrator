package cn.longer233.gamenarrator.mobile;

public final class KeyframeEasing {
    public static final String LINEAR = "LINEAR";
    public static final String EASE_IN = "EASE_IN";
    public static final String EASE_OUT = "EASE_OUT";
    public static final String EASE_IN_OUT = "EASE_IN_OUT";

    private static final String[] VALUES = {LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT};

    private KeyframeEasing() { }

    public static String[] values() {
        return VALUES.clone();
    }

    public static boolean isSupported(String value) {
        for (String candidate : VALUES) {
            if (candidate.equals(value)) return true;
        }
        return false;
    }

    public static int index(String value) {
        for (int i = 0; i < VALUES.length; i++) {
            if (VALUES[i].equals(value)) return i;
        }
        return 0;
    }

    public static float apply(String easing, float raw) {
        float t = Math.max(0f, Math.min(1f, raw));
        if (EASE_IN.equals(easing)) return t * t;
        if (EASE_OUT.equals(easing)) return 1f - (1f - t) * (1f - t);
        if (EASE_IN_OUT.equals(easing)) {
            return t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2f) / 2f;
        }
        return t;
    }
}
