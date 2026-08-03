package cn.longer233.gamenarrator.media;

public record MediaMetadata(
        double durationSeconds,
        int width,
        int height,
        double framesPerSecond,
        String videoCodec,
        String audioCodec
) {
}
