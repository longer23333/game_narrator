package cn.longer233.gamenarrator.transcription;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WhisperTranscriptionCacheTest {

    @Test
    void reusesPreviousTranscriptWhenAudioAndModelAreUnchanged(@TempDir Path directory) throws Exception {
        Path executable = Files.write(directory.resolve("whisper-cli.exe"), new byte[]{1});
        Path model = Files.write(directory.resolve("ggml-base.bin"), new byte[]{1});
        Path audio = Files.write(directory.resolve("audio.wav"), new byte[]{2, 3, 4, 5});
        Path text = Files.writeString(directory.resolve("transcript.txt"), "缓存命中的转写文本",
                StandardCharsets.UTF_8);
        Path subtitle = Files.writeString(directory.resolve("transcript.srt"), "1\n00:00:00,000 --> 00:00:01,000\n缓存\n",
                StandardCharsets.UTF_8);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> cache = new LinkedHashMap<>();
        cache.put("audioPath", audio.toAbsolutePath().normalize().toString());
        cache.put("audioSize", Files.size(audio));
        cache.put("audioModified", Files.getLastModifiedTime(audio).toMillis());
        cache.put("modelPath", model.toAbsolutePath().normalize().toString());
        cache.put("modelSize", Files.size(model));
        cache.put("modelModified", Files.getLastModifiedTime(model).toMillis());
        cache.put("language", "zh");
        cache.put("textPath", text.toString());
        cache.put("subtitlePath", subtitle.toString());
        cache.put("jsonPath", "");
        mapper.writeValue(directory.resolve("transcript.cache.json").toFile(), cache);

        WhisperCppTranscriber transcriber = new WhisperCppTranscriber(executable.toString(), model.toString(),
                "zh", 4, 30, 20, 45, mapper);
        TranscriptionResult result = transcriber.transcribe(audio);

        assertThat(result.text()).isEqualTo("缓存命中的转写文本");
        assertThat(result.textPath()).isEqualTo(text.toString());
        assertThat(result.subtitlePath()).isEqualTo(subtitle.toString());
    }

    @Test
    void ignoresCacheWhenAudioChanged(@TempDir Path directory) throws Exception {
        Path executable = Files.write(directory.resolve("whisper-cli.exe"), new byte[]{1});
        Path model = Files.write(directory.resolve("ggml-base.bin"), new byte[]{1});
        Path audio = Files.write(directory.resolve("audio.wav"), new byte[]{2, 3, 4, 5});
        Path text = Files.writeString(directory.resolve("transcript.txt"), "旧结果", StandardCharsets.UTF_8);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> cache = new LinkedHashMap<>();
        cache.put("audioPath", audio.toAbsolutePath().normalize().toString());
        cache.put("audioSize", 999L);
        cache.put("audioModified", Files.getLastModifiedTime(audio).toMillis());
        cache.put("modelPath", model.toAbsolutePath().normalize().toString());
        cache.put("modelSize", Files.size(model));
        cache.put("modelModified", Files.getLastModifiedTime(model).toMillis());
        cache.put("language", "zh");
        cache.put("textPath", text.toString());
        cache.put("subtitlePath", "");
        cache.put("jsonPath", "");
        mapper.writeValue(directory.resolve("transcript.cache.json").toFile(), cache);

        WhisperCppTranscriber transcriber = new WhisperCppTranscriber(executable.toString(), model.toString(),
                "zh", 4, 30, 20, 45, mapper);
        // 缓存失效后会真的去调用（这里执行体是假 exe，必定失败）——正好证明没有走缓存。
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transcriber.transcribe(audio))
                .isInstanceOf(IllegalStateException.class);
    }
}
