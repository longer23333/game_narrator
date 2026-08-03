package cn.longer233.gamenarrator.voice;

import cn.longer233.gamenarrator.common.AtomicArtifactWriter;
import cn.longer233.gamenarrator.script.ScriptSegment;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.*;

@Component
public class SilentVoiceGenerator {
    private final ObjectMapper mapper;
    public SilentVoiceGenerator(ObjectMapper mapper) { this.mapper = mapper; }

    public VoiceGenerationResult generate(Path scriptPath) {
        try {
            List<ScriptSegment> scripts = mapper.readerForListOf(ScriptSegment.class)
                    .readValue(mapper.readTree(scriptPath.toFile()).path("segments"));
            List<VoiceSegment> result = new ArrayList<>();
            AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
            for (ScriptSegment script : scripts) {
                Path wav = scriptPath.getParent().resolve("silence-" + script.clipIndex() + ".wav");
                byte[] pcm = new byte[3200]; // 100 ms valid silence keeps the normal renderer path intact.
                try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(pcm), format, pcm.length / 2)) {
                    AudioSystem.write(stream, AudioFileFormat.Type.WAVE, wav.toFile());
                }
                result.add(new VoiceSegment(script.clipIndex(), wav.toString(), "", "NONE", 1.0));
            }
            Path manifest = scriptPath.getParent().resolve("voice-manifest.json");
            AtomicArtifactWriter.writeJson(mapper, manifest, Map.of("engine", "NONE", "segments", result));
            return new VoiceGenerationResult(manifest.toString(), result);
        } catch (Exception exception) {
            throw new IllegalStateException("创建无配音轨道失败：" + exception.getMessage(), exception);
        }
    }
}
