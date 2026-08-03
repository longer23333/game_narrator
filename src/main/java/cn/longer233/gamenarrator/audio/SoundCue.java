package cn.longer233.gamenarrator.audio;

public record SoundCue(
        int sequence,
        double startSeconds,
        String type,
        String audioPath,
        double volume
) {
}
