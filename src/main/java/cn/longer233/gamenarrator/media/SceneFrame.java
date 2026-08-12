package cn.longer233.gamenarrator.media;

public record SceneFrame(int index, double timestampSeconds, String imagePath,
                         String samplingReason, Double eventAnchorSeconds) {
    public SceneFrame(int index, double timestampSeconds, String imagePath) {
        this(index, timestampSeconds, imagePath, "SCENE_CHANGE", null);
    }
}
