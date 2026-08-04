package cn.longer233.gamenarrator.render;

import cn.longer233.gamenarrator.audio.SoundCue;
import cn.longer233.gamenarrator.timeline.TimelineSegment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/** Builds the FFmpeg audio mix graph while leaving process and file orchestration to the renderer. */
@Component
public class RenderAudioMixBuilder {
    public AudioMixPlan build(List<TimelineSegment> segments, List<SoundCue> soundCues,
                              List<RenderAssetResolver.RenderAsset> externalAudio,
                              double sourceAudioVolume) {
        if (segments == null || segments.isEmpty()) throw new IllegalArgumentException("剪辑时间线不能为空");
        List<SoundCue> cues = soundCues == null ? List.of() : soundCues;
        List<RenderAssetResolver.RenderAsset> assets = externalAudio == null ? List.of() : externalAudio;
        StringBuilder filter = new StringBuilder("[0:a]volume=")
                .append(decimal(sourceAudioVolume)).append("[bg];");
        for (int index = 0; index < segments.size(); index++) {
            TimelineSegment segment = segments.get(index);
            long delay = Math.round(segment.outputStartSeconds() * 1000);
            filter.append('[').append(index + 1).append(":a]");
            double clipDuration = segment.outputEndSeconds() - segment.outputStartSeconds();
            if (segment.voiceDurationSeconds() > clipDuration - 0.25) {
                double speed = Math.min(2.0,
                        segment.voiceDurationSeconds() / Math.max(0.5, clipDuration - 0.25));
                filter.append("atempo=").append(decimal(speed)).append(',');
            }
            filter.append("adelay=").append(delay).append('|').append(delay)
                    .append("[v").append(index).append("];");
        }
        for (int index = 0; index < cues.size(); index++) {
            SoundCue cue = cues.get(index);
            int inputIndex = segments.size() + 1 + index;
            long delay = Math.round(cue.startSeconds() * 1000);
            filter.append('[').append(inputIndex).append(":a]volume=")
                    .append(decimal(cue.volume())).append(",adelay=")
                    .append(delay).append('|').append(delay).append("[s").append(index).append("];");
        }
        double totalDuration = segments.getLast().outputEndSeconds();
        for (int index = 0; index < assets.size(); index++) {
            RenderAssetResolver.RenderAsset asset = assets.get(index);
            int inputIndex = segments.size() + 1 + cues.size() + index;
            TimelineSegment segment = segments.stream().filter(item -> item.sequence() == asset.clipIndex())
                    .findFirst().orElse(segments.getFirst());
            filter.append('[').append(inputIndex).append(":a]");
            if ("BACKGROUND_AUDIO".equals(asset.placementType())) {
                filter.append("atrim=0:").append(decimal(totalDuration)).append(",volume=0.14");
            } else {
                long delay = Math.round(segment.outputStartSeconds() * 1000);
                filter.append("atrim=0:").append(decimal(segment.outputEndSeconds() - segment.outputStartSeconds()))
                        .append(",volume=0.48,adelay=").append(delay).append('|').append(delay);
            }
            filter.append("[x").append(index).append("];");
        }
        for (int index = 0; index < segments.size(); index++) filter.append("[v").append(index).append(']');
        for (int index = 0; index < cues.size(); index++) filter.append("[s").append(index).append(']');
        for (int index = 0; index < assets.size(); index++) filter.append("[x").append(index).append(']');
        filter.append("amix=inputs=").append(segments.size() + cues.size() + assets.size())
                .append(":duration=longest:normalize=0,asplit=2[voiceSide][voiceMix];")
                .append("[bg][voiceSide]sidechaincompress=threshold=0.02:ratio=8:attack=20:release=320[ducked];")
                .append("[ducked][voiceMix]amix=inputs=2:duration=first:dropout_transition=0[aout]");
        int subtitleInput = segments.size() + cues.size() + assets.size() + 1;
        return new AudioMixPlan(filter.toString(), subtitleInput);
    }

    private String decimal(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    public record AudioMixPlan(String filterGraph, int subtitleInput) { }
}
