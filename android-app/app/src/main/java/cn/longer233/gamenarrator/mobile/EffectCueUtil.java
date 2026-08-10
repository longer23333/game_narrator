package cn.longer233.gamenarrator.mobile;

public final class EffectCueUtil {
    private EffectCueUtil() { }

    public static boolean isValidName(String name) {
        if (name == null || name.trim().isBlank()) return false;
        return name.trim().length() <= 40;
    }

    public static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public static String combine(String existing, String templateCue) {
        String left = clean(existing);
        String right = clean(templateCue);
        if (left.isBlank()) return right;
        if (right.isBlank() || left.equals(right) || left.contains(right)) return left;
        return left + " / " + right;
    }

    public static boolean hasCrossfade(String value) {
        return value != null && value.contains("交叉转场");
    }
}
