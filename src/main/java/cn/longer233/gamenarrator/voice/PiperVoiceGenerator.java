package cn.longer233.gamenarrator.voice;

import cn.longer233.gamenarrator.script.ScriptSegment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

@Component
public class PiperVoiceGenerator {
    private static final Logger log = LoggerFactory.getLogger(PiperVoiceGenerator.class);
    private final ObjectMapper objectMapper;
    private final Path executable;
    private final Map<String, ConfiguredVoice> voices;
    private final String defaultVoiceId;
    private final double lengthScale;
    private final Path ffmpeg;
    private final Path previewDirectory;
    private final Map<String, ConfiguredProfile> profiles;
    private final String defaultProfileId;

    public PiperVoiceGenerator(ObjectMapper objectMapper, PiperProperties properties,
            @Value("${game-narrator.ffmpeg-command:ffmpeg}") String ffmpegCommand,
            @Value("${game-narrator.storage-root:./storage}") String storageRoot) {
        this.objectMapper = objectMapper;
        this.executable = Path.of(properties.getExecutable()).toAbsolutePath().normalize();
        this.lengthScale = properties.getLengthScale();
        LinkedHashMap<String, ConfiguredVoice> configured = new LinkedHashMap<>();
        for (PiperProperties.Voice voice : properties.getVoices()) {
            if (voice.getId() == null || voice.getId().isBlank() || voice.getModel() == null || voice.getModel().isBlank()) continue;
            configured.put(voice.getId(), new ConfiguredVoice(voice.getId(),
                    voice.getName() == null || voice.getName().isBlank() ? voice.getId() : voice.getName(),
                    Path.of(voice.getModel()).toAbsolutePath().normalize()));
        }
        if (configured.isEmpty()) {
            configured.put("default", new ConfiguredVoice("default", "默认中文音色",
                    Path.of(properties.getModel()).toAbsolutePath().normalize()));
        }
        this.voices = Map.copyOf(configured);
        this.defaultVoiceId = configured.containsKey(properties.getDefaultVoice())
                ? properties.getDefaultVoice() : configured.keySet().iterator().next();
        this.ffmpeg = Path.of(ffmpegCommand).toAbsolutePath().normalize();
        this.previewDirectory = Path.of(storageRoot).toAbsolutePath().normalize().resolve("voice-previews");
        LinkedHashMap<String, ConfiguredProfile> configuredProfiles = new LinkedHashMap<>();
        for (PiperProperties.Profile profile : properties.getProfiles()) {
            if (profile.getId() == null || profile.getId().isBlank()) continue;
            String voiceId = profile.getVoiceId() == null || profile.getVoiceId().isBlank()
                    ? defaultVoiceId : profile.getVoiceId().strip();
            configuredProfiles.put(profile.getId().strip(), new ConfiguredProfile(profile.getId().strip(),
                    profile.getName() == null || profile.getName().isBlank() ? profile.getId().strip() : profile.getName().strip(),
                    voiceId, normalizeEmotion(profile.getEmotion()), boundedSpeed(profile.getSpeed()),
                    boundedPitch(profile.getPitchSemitones())));
        }
        if (configuredProfiles.isEmpty()) configuredProfiles.put("narrative", new ConfiguredProfile(
                "narrative", "自然解说", defaultVoiceId, "NEUTRAL", 1.0, 0));
        this.profiles = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(configuredProfiles));
        this.defaultProfileId = configuredProfiles.containsKey(properties.getDefaultProfile())
                ? properties.getDefaultProfile() : configuredProfiles.keySet().iterator().next();
    }

    public String engineId() { return "piper"; }

    public boolean available() {
        return Files.isRegularFile(executable) && voices.values().stream().anyMatch(voice -> Files.isRegularFile(voice.model()));
    }

    public List<VoiceOption> options() {
        return voices.values().stream().map(voice -> new VoiceOption(voice.id(), voice.name(),
                Files.isRegularFile(executable) && Files.isRegularFile(voice.model()),
                voice.id().equals(defaultVoiceId))).toList();
    }

    public List<VoiceProfile> profiles() {
        return profiles.values().stream().map(profile -> new VoiceProfile(profile.id(), profile.name(),
                profile.voiceId(), profile.emotion(), profile.speed(), profile.pitch(),
                voiceAvailable(profile.voiceId()), profile.id().equals(defaultProfileId))).toList();
    }

    public VoiceGenerationResult generate(Path scriptPath) {
        return generate(scriptPath, ignored -> { });
    }

    public VoiceGenerationResult generate(Path scriptPath, IntConsumer progress) {
        if (!available()) {
            throw new IllegalStateException("等待本地 Piper 配音引擎；请执行 .\\scripts\\setup-piper.ps1");
        }
        try {
            JsonNode document = objectMapper.readTree(scriptPath.toFile());
            List<ScriptSegment> scripts = objectMapper.readerForListOf(ScriptSegment.class)
                    .readValue(document.path("segments"));
            if (scripts.isEmpty()) throw new IllegalStateException("生成文案中没有可配音片段");
            Path voiceDirectory = scriptPath.getParent().resolve("voice");
            Files.createDirectories(voiceDirectory);
            ResolvedProfile resolved = resolve(null);
            ConfiguredVoice voice = requireVoice(resolved.voiceId());
            log.info("VOICE_GENERATION_BEGIN engine=piper segmentCount={} voiceId={}", scripts.size(), voice.id());
            List<VoiceSegment> voices = new ArrayList<>();
            for (int index = 0; index < scripts.size(); index++) {
                ScriptSegment script = scripts.get(index);
                Path output = voiceDirectory.resolve("voice-%02d.wav".formatted(script.clipIndex()));
                synthesize(script.narration(), output, voice.model(), resolved.speed());
                applyPitch(output, resolved.pitch());
                voices.add(new VoiceSegment(script.clipIndex(), output.toString(), script.narration(), voice.id(),
                        resolved.speed(), resolved.profileId(), resolved.emotion(), resolved.pitch()));
                log.info("VOICE_SEGMENT_SUCCESS clipIndex={} characters={} output={}",
                        script.clipIndex(), script.narration().length(), output);
                progress.accept(10 + (int) Math.round((index + 1) * 85.0 / scripts.size()));
            }
            Path manifest = scriptPath.getParent().resolve("voice-manifest.json");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("engine", "piper");
            result.put("voiceId", voice.id());
            result.put("profileId", resolved.profileId());
            result.put("emotion", resolved.emotion());
            result.put("speed", resolved.speed());
            result.put("pitchSemitones", resolved.pitch());
            result.put("model", voice.model().toString());
            result.put("segments", voices);
            cn.longer233.gamenarrator.common.AtomicArtifactWriter.writeJson(objectMapper, manifest, result);
            log.info("VOICE_GENERATION_SUCCESS segmentCount={} output={}", voices.size(), manifest);
            return new VoiceGenerationResult(manifest.toString(), voices);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("AI 配音生成失败：" + exception.getMessage(), exception);
        }
    }

    public VoiceSegment regenerateSegment(Path scriptPath, int clipIndex) {
        return regenerateSegment(scriptPath, clipIndex, (VoiceRegenerationRequest) null);
    }

    public VoiceSegment regenerateSegment(Path scriptPath, int clipIndex, String voiceId, double speed) {
        return regenerateSegment(scriptPath, clipIndex,
                new VoiceRegenerationRequest(voiceId, speed, null, null, null));
    }

    public VoiceSegment regenerateSegment(Path scriptPath, int clipIndex, VoiceRegenerationRequest request) {
        if (!available()) {
            throw new IllegalStateException("Piper is not available");
        }
        try {
            ResolvedProfile resolved = resolve(request);
            ConfiguredVoice voice = requireVoice(resolved.voiceId());
            JsonNode document = objectMapper.readTree(scriptPath.toFile());
            List<ScriptSegment> scripts = objectMapper.readerForListOf(ScriptSegment.class)
                    .readValue(document.path("segments"));
            ScriptSegment script = scripts.stream()
                    .filter(item -> item.clipIndex() == clipIndex)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Script segment does not exist: " + clipIndex));
            Path voiceDirectory = scriptPath.getParent().resolve("voice");
            Files.createDirectories(voiceDirectory);
            Path output = voiceDirectory.resolve("voice-%02d.wav".formatted(clipIndex));
            Path temporary = voiceDirectory.resolve(".voice-%02d-%s.wav.part".formatted(clipIndex,
                    java.util.UUID.randomUUID()));
            try {
                synthesize(script.narration(), temporary, voice.model(), resolved.speed());
                applyPitch(temporary, resolved.pitch());
                try {
                    Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                    Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            VoiceSegment regenerated = new VoiceSegment(clipIndex, output.toString(), script.narration(), voice.id(),
                    resolved.speed(), resolved.profileId(), resolved.emotion(), resolved.pitch());

            Path manifest = scriptPath.getParent().resolve("voice-manifest.json");
            List<VoiceSegment> voices = new ArrayList<>();
            if (Files.isRegularFile(manifest)) {
                JsonNode existing = objectMapper.readTree(manifest.toFile());
                voices.addAll(objectMapper.readerForListOf(VoiceSegment.class)
                        .readValue(existing.path("segments")));
            }
            voices.removeIf(item -> item.clipIndex() == clipIndex);
            voices.add(regenerated);
            voices.sort(java.util.Comparator.comparingInt(VoiceSegment::clipIndex));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("engine", engineId());
            result.put("voiceId", voice.id());
            result.put("model", voice.model().toString());
            result.put("segments", voices);
            cn.longer233.gamenarrator.common.AtomicArtifactWriter.writeJson(objectMapper, manifest, result);
            log.info("VOICE_SEGMENT_REGENERATED engine={} clipIndex={} output={}",
                    engineId(), clipIndex, output);
            return regenerated;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Voice segment regeneration failed: " + exception.getMessage(), exception);
        }
    }

    public Path preview(VoiceRegenerationRequest request, String text) {
        if (!available()) throw new IllegalStateException("Piper is not available");
        String sample = text == null || text.isBlank() ? "欢迎使用游戏解说配音试听。" : text.strip();
        if (sample.length() > 160) throw new IllegalArgumentException("Preview text cannot exceed 160 characters");
        try {
            ResolvedProfile resolved = resolve(request);
            ConfiguredVoice voice = requireVoice(resolved.voiceId());
            Files.createDirectories(previewDirectory);
            Path output = previewDirectory.resolve("preview-" + java.util.UUID.randomUUID() + ".wav");
            synthesize(sample, output, voice.model(), resolved.speed());
            applyPitch(output, resolved.pitch());
            return output;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Voice preview failed: " + exception.getMessage(), exception);
        }
    }

    private void synthesize(String text, Path output, Path model, double speed) throws Exception {
        var result = cn.longer233.gamenarrator.common.ExternalProcessRunner.run(List.of(
                        executable.toString(), "--model", model.toString(), "--output_file", output.toString(),
                        "--length_scale", Double.toString(lengthScale / speed)),
                Duration.ofMinutes(2), text + System.lineSeparator());
        String processOutput = result.output();
        if (result.exitCode() != 0 || !Files.isRegularFile(output)) {
            throw new IllegalStateException("Piper 退出码 " + result.exitCode() + "：" + tail(processOutput, 800));
        }
    }

    private void applyPitch(Path audio, double semitones) throws Exception {
        if (Math.abs(semitones) < .01) return;
        if (!Files.isRegularFile(ffmpeg)) throw new IllegalStateException("FFmpeg is required for pitch control");
        Path transformed = audio.resolveSibling("." + audio.getFileName() + ".pitch.wav");
        double factor = Math.pow(2, semitones / 12.0);
        float sampleRate;
        try (var stream = javax.sound.sampled.AudioSystem.getAudioInputStream(audio.toFile())) {
            sampleRate = stream.getFormat().getSampleRate();
        }
        if (sampleRate <= 0) throw new IllegalStateException("Cannot determine generated voice sample rate");
        String filter = "asetrate=" + sampleRate + "*" + factor + ",aresample=" + sampleRate
                + ",atempo=" + (1.0 / factor);
        try {
            var result = cn.longer233.gamenarrator.common.ExternalProcessRunner.run(List.of(ffmpeg.toString(),
                    "-hide_banner", "-loglevel", "error", "-y", "-i", audio.toString(), "-af", filter,
                    transformed.toString()), Duration.ofMinutes(2));
            if (result.exitCode() != 0 || !Files.isRegularFile(transformed))
                throw new IllegalStateException("FFmpeg pitch processing failed: " + tail(result.output(), 800));
            Files.move(transformed, audio, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(transformed);
        }
    }

    private ResolvedProfile resolve(VoiceRegenerationRequest request) {
        ConfiguredProfile profile = profiles.get(request == null || request.profileId() == null
                || request.profileId().isBlank() ? defaultProfileId : request.profileId());
        if (profile == null) throw new IllegalArgumentException("Unknown voice profile: " + request.profileId());
        String voiceId = request != null && request.voiceId() != null && !request.voiceId().isBlank()
                ? request.voiceId().strip() : profile.voiceId();
        String emotion = request != null && request.emotion() != null && !request.emotion().isBlank()
                ? normalizeEmotion(request.emotion()) : profile.emotion();
        double speed = request != null && request.speed() != null ? request.effectiveSpeed() : profile.speed();
        double pitch = request != null && request.pitchSemitones() != null ? request.effectivePitch() : profile.pitch();
        double[] adjustment = switch (emotion) {
            case "EXCITED" -> new double[]{1.08, 1.0};
            case "CALM" -> new double[]{.92, -.5};
            case "TENSE" -> new double[]{1.04, .5};
            case "SAD" -> new double[]{.88, -1.0};
            default -> new double[]{1, 0};
        };
        return new ResolvedProfile(profile.id(), voiceId, emotion, boundedSpeed(speed * adjustment[0]),
                boundedPitch(pitch + adjustment[1]));
    }

    private boolean voiceAvailable(String voiceId) {
        ConfiguredVoice value = voices.get(voiceId);
        return value != null && Files.isRegularFile(executable) && Files.isRegularFile(value.model());
    }
    private double boundedSpeed(double value) {
        if (!Double.isFinite(value) || value < .5 || value > 2) throw new IllegalArgumentException("Voice speed must be between 0.5 and 2.0");
        return value;
    }
    private double boundedPitch(double value) {
        if (!Double.isFinite(value) || value < -6 || value > 6) throw new IllegalArgumentException("Voice pitch must be between -6 and 6 semitones");
        return value;
    }
    private String normalizeEmotion(String value) {
        String normalized = value == null ? "NEUTRAL" : value.strip().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("NEUTRAL", "EXCITED", "CALM", "TENSE", "SAD").contains(normalized))
            throw new IllegalArgumentException("Unsupported voice emotion: " + value);
        return normalized;
    }

    private String tail(String value, int limit) {
        return value.length() <= limit ? value : value.substring(value.length() - limit);
    }

    private ConfiguredVoice requireVoice(String voiceId) {
        ConfiguredVoice voice = voices.get(voiceId);
        if (voice == null) throw new IllegalArgumentException("Unknown configured voice: " + voiceId);
        if (!Files.isRegularFile(voice.model())) throw new IllegalStateException("Voice model is not installed: " + voice.name());
        return voice;
    }

    private record ConfiguredVoice(String id, String name, Path model) {}
    private record ConfiguredProfile(String id, String name, String voiceId, String emotion,
                                     double speed, double pitch) { }
    private record ResolvedProfile(String profileId, String voiceId, String emotion,
                                   double speed, double pitch) { }
}
