package cn.longer233.gamenarrator.mobile;

public final class DerivedAssetName {
    private DerivedAssetName() { }

    public static String base(String clipName) {
        String safe = clipName == null ? "" : clipName.replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("^_+|_+$", "");
        return safe.isBlank() ? "clip" : safe;
    }

    public static String coverFileName(String clipName, long stamp) {
        return base(clipName) + "-cover-" + stamp + ".png";
    }
}
