package cn.longer233.gamenarrator.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PiperVoiceProfileTest {
    @Test
    void exposesConfiguredProfilesAndTheirInstallationState(@TempDir Path directory) throws Exception {
        Path executable = Files.write(directory.resolve("piper.exe"), new byte[]{1});
        Path installedModel = Files.write(directory.resolve("installed.onnx"), new byte[]{1});
        PiperProperties properties = new PiperProperties();
        properties.setExecutable(executable.toString());
        properties.setDefaultVoice("installed");
        properties.setDefaultProfile("highlight");
        PiperProperties.Voice installed = voice("installed", "Installed", installedModel.toString());
        PiperProperties.Voice missing = voice("missing", "Missing", directory.resolve("missing.onnx").toString());
        properties.setVoices(List.of(installed, missing));
        properties.setProfiles(List.of(
                profile("highlight", "High energy", "installed", "EXCITED", 1, .5),
                profile("missing-profile", "Unavailable", "missing", "CALM", 1, 0)));

        PiperVoiceGenerator generator = new PiperVoiceGenerator(new ObjectMapper(), properties,
                directory.resolve("ffmpeg.exe").toString(), directory.toString());

        assertThat(generator.profiles()).containsExactly(
                new VoiceProfile("highlight", "High energy", "installed", "EXCITED", 1, .5, true, true),
                new VoiceProfile("missing-profile", "Unavailable", "missing", "CALM", 1, 0, false, false));
    }

    private PiperProperties.Voice voice(String id, String name, String model) {
        var value = new PiperProperties.Voice(); value.setId(id); value.setName(name); value.setModel(model); return value;
    }
    private PiperProperties.Profile profile(String id, String name, String voiceId, String emotion,
            double speed, double pitch) {
        var value = new PiperProperties.Profile(); value.setId(id); value.setName(name); value.setVoiceId(voiceId);
        value.setEmotion(emotion); value.setSpeed(speed); value.setPitchSemitones(pitch); return value;
    }
}
