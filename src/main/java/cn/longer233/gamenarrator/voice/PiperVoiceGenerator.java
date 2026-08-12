package cn.longer233.gamenarrator.voice;

import cn.longer233.gamenarrator.script.ScriptSegment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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

    public PiperVoiceGenerator(ObjectMapper objectMapper, PiperProperties properties) {
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
            ConfiguredVoice voice = requireVoice(defaultVoiceId);
            log.info("VOICE_GENERATION_BEGIN engine=piper segmentCount={} voiceId={}", scripts.size(), voice.id());
            List<VoiceSegment> voices = new ArrayList<>();
            for (int index = 0; index < scripts.size(); index++) {
                ScriptSegment script = scripts.get(index);
                Path output = voiceDirectory.resolve("voice-%02d.wav".formatted(script.clipIndex()));
                synthesize(script.narration(), output, voice.model(), 1.0);
                voices.add(new VoiceSegment(script.clipIndex(), output.toString(), script.narration(), voice.id(), 1.0));
                log.info("VOICE_SEGMENT_SUCCESS clipIndex={} characters={} output={}",
                        script.clipIndex(), script.narration().length(), output);
                progress.accept(10 + (int) Math.round((index + 1) * 85.0 / scripts.size()));
            }
            Path manifest = scriptPath.getParent().resolve("voice-manifest.json");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("engine", "piper");
            result.put("voiceId", voice.id());
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
        return regenerateSegment(scriptPath, clipIndex, defaultVoiceId, 1.0);
    }

    public VoiceSegment regenerateSegment(Path scriptPath, int clipIndex, String voiceId, double speed) {
        if (!available()) {
            throw new IllegalStateException("Piper is not available");
        }
        if (!Double.isFinite(speed) || speed < 0.5 || speed > 2.0) {
            throw new IllegalArgumentException("Voice speed must be between 0.5 and 2.0");
        }
        try {
            ConfiguredVoice voice = requireVoice(voiceId == null || voiceId.isBlank() ? defaultVoiceId : voiceId);
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
                synthesize(script.narration(), temporary, voice.model(), speed);
                try {
                    Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                    Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            VoiceSegment regenerated = new VoiceSegment(clipIndex, output.toString(), script.narration(), voice.id(), speed);

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
}
