package cn.longer233.gamenarrator.audio;

import cn.longer233.gamenarrator.effect.EffectPlan;
import cn.longer233.gamenarrator.effect.TransitionType;
import cn.longer233.gamenarrator.effect.VisualEffectType;
import cn.longer233.gamenarrator.timeline.TimelineSegment;
import org.springframework.stereotype.Component;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class ProceduralSoundEffectLibrary {
    private static final int SAMPLE_RATE = 48_000;

    public List<SoundCue> create(Path taskDirectory, List<TimelineSegment> segments,
                                 List<EffectPlan> plans) {
        try {
            Path directory = taskDirectory.resolve("sound-effects");
            Files.createDirectories(directory);
            List<SoundCue> cues = new ArrayList<>();
            for (int index = 0; index < segments.size(); index++) {
                TimelineSegment segment = segments.get(index);
                EffectPlan plan = plans.get(index);
                String type = select(plan);
                if (type == null) continue;
                Path output = directory.resolve("%02d-%s.wav".formatted(segment.sequence(), type.toLowerCase()));
                write(output, samples(type));
                cues.add(new SoundCue(segment.sequence(), segment.outputStartSeconds(), type,
                        output.toString(), volume(type)));
            }
            return cues;
        } catch (Exception exception) {
            throw new IllegalStateException("生成程序化音效失败：" + exception.getMessage(), exception);
        }
    }

    private String select(EffectPlan plan) {
        if (plan.transition() == TransitionType.ANIME_IMPACT
                || plan.effects().contains(VisualEffectType.WHITE_FLASH)) return "IMPACT";
        if (plan.transition() == TransitionType.PUSH
                || plan.effects().contains(VisualEffectType.SPEED_LINES)) return "WHOOSH";
        if (plan.effects().contains(VisualEffectType.FREEZE_ACCENT)) return "COMEDY";
        return null;
    }

    private double[] samples(String type) {
        return switch (type) {
            case "IMPACT" -> impact(0.38);
            case "WHOOSH" -> whoosh(0.48);
            default -> comedy(0.34);
        };
    }

    private double[] impact(double seconds) {
        int length = (int) (SAMPLE_RATE * seconds);
        double[] result = new double[length];
        Random random = new Random(233);
        for (int i = 0; i < length; i++) {
            double time = i / (double) SAMPLE_RATE;
            double envelope = Math.exp(-9 * time);
            result[i] = envelope * (0.72 * Math.sin(2 * Math.PI * (78 - 30 * time) * time)
                    + 0.18 * (random.nextDouble() * 2 - 1));
        }
        return result;
    }

    private double[] whoosh(double seconds) {
        int length = (int) (SAMPLE_RATE * seconds);
        double[] result = new double[length];
        Random random = new Random(404);
        double smooth = 0;
        for (int i = 0; i < length; i++) {
            double progress = i / (double) length;
            smooth = smooth * 0.86 + (random.nextDouble() * 2 - 1) * 0.14;
            double envelope = Math.sin(Math.PI * progress);
            result[i] = smooth * envelope * 0.65;
        }
        return result;
    }

    private double[] comedy(double seconds) {
        int length = (int) (SAMPLE_RATE * seconds);
        double[] result = new double[length];
        for (int i = 0; i < length; i++) {
            double time = i / (double) SAMPLE_RATE;
            double frequency = time < seconds / 2 ? 520 : 690;
            double envelope = Math.min(1, time * 30) * Math.exp(-3.5 * time);
            result[i] = Math.sin(2 * Math.PI * frequency * time) * envelope * 0.52;
        }
        return result;
    }

    private void write(Path path, double[] samples) throws Exception {
        byte[] pcm = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            short value = (short) Math.round(Math.max(-1, Math.min(1, samples[i])) * 32767);
            pcm[i * 2] = (byte) (value & 0xff);
            pcm[i * 2 + 1] = (byte) ((value >>> 8) & 0xff);
        }
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        try (var stream = new AudioInputStream(new ByteArrayInputStream(pcm), format, samples.length)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, path.toFile());
        }
    }

    private double volume(String type) {
        return "IMPACT".equals(type) ? 0.72 : "WHOOSH".equals(type) ? 0.55 : 0.48;
    }
}
