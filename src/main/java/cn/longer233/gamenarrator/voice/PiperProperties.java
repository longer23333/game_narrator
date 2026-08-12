package cn.longer233.gamenarrator.voice;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "game-narrator.piper")
public class PiperProperties {
    private String executable = "./tools/piper/piper/piper.exe";
    private String model = "./models/piper/zh_CN-huayan-medium.onnx";
    private double lengthScale = 1.0;
    private String defaultVoice = "default";
    private List<Voice> voices = new ArrayList<>();
    private String defaultProfile = "narrative";
    private List<Profile> profiles = new ArrayList<>();

    public String getExecutable() { return executable; }
    public void setExecutable(String executable) { this.executable = executable; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public double getLengthScale() { return lengthScale; }
    public void setLengthScale(double lengthScale) { this.lengthScale = lengthScale; }
    public String getDefaultVoice() { return defaultVoice; }
    public void setDefaultVoice(String defaultVoice) { this.defaultVoice = defaultVoice; }
    public List<Voice> getVoices() { return voices; }
    public void setVoices(List<Voice> voices) { this.voices = voices == null ? new ArrayList<>() : voices; }
    public String getDefaultProfile() { return defaultProfile; }
    public void setDefaultProfile(String defaultProfile) { this.defaultProfile = defaultProfile; }
    public List<Profile> getProfiles() { return profiles; }
    public void setProfiles(List<Profile> profiles) { this.profiles = profiles == null ? new ArrayList<>() : profiles; }

    public static class Voice {
        private String id;
        private String name;
        private String model;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }

    public static class Profile {
        private String id;
        private String name;
        private String voiceId;
        private String emotion = "NEUTRAL";
        private double speed = 1.0;
        private double pitchSemitones;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getVoiceId() { return voiceId; }
        public void setVoiceId(String voiceId) { this.voiceId = voiceId; }
        public String getEmotion() { return emotion; }
        public void setEmotion(String emotion) { this.emotion = emotion; }
        public double getSpeed() { return speed; }
        public void setSpeed(double speed) { this.speed = speed; }
        public double getPitchSemitones() { return pitchSemitones; }
        public void setPitchSemitones(double pitchSemitones) { this.pitchSemitones = pitchSemitones; }
    }
}
