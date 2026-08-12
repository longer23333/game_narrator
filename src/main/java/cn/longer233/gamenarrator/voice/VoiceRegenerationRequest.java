package cn.longer233.gamenarrator.voice;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

public record VoiceRegenerationRequest(
        @Size(max = 60) String voiceId,
        @DecimalMin("0.5") @DecimalMax("2.0") Double speed,
        @Size(max = 60) String profileId,
        @Size(max = 20) String emotion,
        @DecimalMin("-6.0") @DecimalMax("6.0") Double pitchSemitones
) {
    public double effectiveSpeed() {
        return speed == null ? 1.0 : speed;
    }
    public double effectivePitch() { return pitchSemitones == null ? 0 : pitchSemitones; }
}
