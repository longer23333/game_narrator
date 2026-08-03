package cn.longer233.gamenarrator.voice;

import java.util.List;

public record VoiceGenerationResult(String manifestPath, List<VoiceSegment> segments) {
}
