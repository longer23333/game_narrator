package cn.longer233.gamenarrator.voice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record VoicePreviewRequest(@Valid VoiceRegenerationRequest settings,
        @Size(max = 160) String text) { }
