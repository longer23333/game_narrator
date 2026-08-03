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
}
