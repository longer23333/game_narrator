package cn.longer233.gamenarrator.voice;

public record VoiceProfile(String id, String name, String voiceId, String emotion,
        double speed, double pitchSemitones, boolean available, boolean defaultProfile) { }
