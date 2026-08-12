package cn.longer233.gamenarrator.voice;

public record VoiceSegment(int clipIndex, String audioPath, String narration, String voiceId, Double speed,
        String profileId, String emotion, Double pitchSemitones) {
    public VoiceSegment(int clipIndex, String audioPath, String narration) {
        this(clipIndex, audioPath, narration, null, null, null, null, null);
    }
    public VoiceSegment(int clipIndex, String audioPath, String narration, String voiceId, Double speed) {
        this(clipIndex, audioPath, narration, voiceId, speed, null, null, null);
    }
}
