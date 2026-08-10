package cn.longer233.gamenarrator.mobile;

public final class ScreenAdapter {
    private ScreenAdapter() { }

    public static boolean compact(int screenWidthDp) {
        return screenWidthDp > 0 && screenWidthDp < 360;
    }

    public static int editorPreviewHeight(boolean landscape, int screenWidthDp) {
        if (compact(screenWidthDp)) return landscape ? 150 : 180;
        return landscape ? 210 : 250;
    }

    public static int touchTarget(int baseDp, boolean compact) {
        return Math.max(48, baseDp);
    }
}
