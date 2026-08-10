package cn.longer233.gamenarrator.mobile;

public final class PipelineStages {
    public static final String IMPORT = "IMPORT";
    public static final String TRIM = "TRIM";
    public static final String STORYBOARD = "STORYBOARD";
    public static final String ASSETS = "ASSETS";
    public static final String REVIEW = "REVIEW";
    public static final String RENDER = "RENDER";

    private static final String[] ORDER = {IMPORT, TRIM, STORYBOARD, ASSETS, REVIEW, RENDER};
    private static final String[] LABELS = {"素材导入", "镜头修剪", "分镜文案", "配音与素材", "版本检查", "视频渲染"};

    private PipelineStages() { }

    public static String[] order() {
        return ORDER.clone();
    }

    public static int index(String stage) {
        for (int i = 0; i < ORDER.length; i++) {
            if (ORDER[i].equals(stage)) return i;
        }
        return -1;
    }

    public static String label(String stage) {
        int value = index(stage);
        return value < 0 ? stage : LABELS[value];
    }

    public static String next(String stage) {
        int value = index(stage);
        return value < 0 || value + 1 >= ORDER.length ? null : ORDER[value + 1];
    }

    public static boolean isCompleted(String stage, String current) {
        return index(stage) >= 0 && index(stage) < index(current);
    }
}
