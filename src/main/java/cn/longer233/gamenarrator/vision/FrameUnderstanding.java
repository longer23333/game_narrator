package cn.longer233.gamenarrator.vision;

public record FrameUnderstanding(
        int index,
        double timestampSeconds,
        String imagePath,
        String description,
        String eventType,
        int excitementScore,
        String ocrText,
        String rawJson
) {
    public FrameUnderstanding(int index, double timestampSeconds, String imagePath, String description,
                              String eventType, int excitementScore, String rawJson) {
        this(index, timestampSeconds, imagePath, description, eventType, excitementScore, "", rawJson);
    }
}
