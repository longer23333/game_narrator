package cn.longer233.gamenarrator.transcription;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhisperCppTranscriberTest {
    @TempDir Path directory;

    @Test
    void missingRuntimeExplainsHowToInstallIt() {
        WhisperCppTranscriber transcriber = new WhisperCppTranscriber(
                directory.resolve("whisper-cli.exe").toString(),
                directory.resolve("ggml-base.bin").toString(), "auto", 2);

        assertThatThrownBy(() -> transcriber.transcribe(directory.resolve("audio.wav")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("setup-whisper.ps1");
    }
}
