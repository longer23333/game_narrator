package cn.longer233.gamenarrator.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 piper 二进制的集成测试：验证"一个进程合成一批（--json-input）"这条路径。
 * 本机没有 piper/model 时自动跳过，不阻塞普通构建。
 */
class PiperBatchSynthesisTest {

    @Test
    void synthesizesAllSegmentsThroughOneBatchInvocation(@TempDir Path directory) throws Exception {
        Path executable = Path.of("tools/piper/piper/piper.exe").toAbsolutePath().normalize();
        Path model = Path.of("models/piper/zh_CN-huayan-medium.onnx").toAbsolutePath().normalize();
        Assumptions.assumeTrue(Files.isRegularFile(executable), "piper executable not installed");
        Assumptions.assumeTrue(Files.isRegularFile(model), "piper model not installed");

        PiperProperties properties = new PiperProperties();
        properties.setExecutable(executable.toString());
        properties.setDefaultVoice("huayan");
        properties.setBatchSize(3);
        var voice = new PiperProperties.Voice();
        voice.setId("huayan");
        voice.setName("huayan");
        voice.setModel(model.toString());
        properties.setVoices(List.of(voice));

        ObjectMapper mapper = new ObjectMapper();
        Path scriptPath = directory.resolve("script.json");
        Map<String, Object> document = new LinkedHashMap<>();
        List<Map<String, Object>> segments = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            Map<String, Object> segment = new LinkedHashMap<>();
            segment.put("clipIndex", index);
            segment.put("startSeconds", index);
            segment.put("endSeconds", index + 1);
            segment.put("narration", "第 " + (index + 1) + " 段批量配音测试。");
            segment.put("subtitle", "第 " + (index + 1) + " 段");
            segment.put("effectCue", "");
            segments.add(segment);
        }
        document.put("segments", segments);
        mapper.writeValue(scriptPath.toFile(), document);

        PiperVoiceGenerator generator = new PiperVoiceGenerator(mapper, properties,
                directory.resolve("ffmpeg.exe").toString(), directory.toString());
        var result = generator.generateDetailed(scriptPath, update -> { });

        assertThat(result.segments()).hasSize(3);
        for (var segment : result.segments()) {
            assertThat(Files.isRegularFile(Path.of(segment.audioPath()))).isTrue();
            assertThat(Files.size(Path.of(segment.audioPath()))).isGreaterThan(1000);
        }
    }
}
