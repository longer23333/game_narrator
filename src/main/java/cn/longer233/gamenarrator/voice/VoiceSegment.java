package cn.longer233.gamenarrator.voice;

public record VoiceSegment(int clipIndex, String audioPath, String narration, String voiceId, Double speed) {
    public VoiceSegment(int clipIndex, String audioPath, String narration) {
        this(clipIndex, audioPath, narration, null, null);
    }
}
