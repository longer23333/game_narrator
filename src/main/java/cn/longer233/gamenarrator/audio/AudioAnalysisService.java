package cn.longer233.gamenarrator.audio;

import cn.longer233.gamenarrator.common.AtomicArtifactWriter;
import cn.longer233.gamenarrator.common.ExternalProcessRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts bounded, one-second audio features for highlight ranking. */
@Component
public class AudioAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(AudioAnalysisService.class);
    private static final Pattern TIME = Pattern.compile("pts_time:([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern SILENCE_START = Pattern.compile("silence_start: ([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern SILENCE_END = Pattern.compile("silence_end: ([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern VALUE = Pattern.compile("lavfi\\.astats\\.Overall\\.(RMS_level|Peak_level)=(-?inf|-?[0-9]+(?:\\.[0-9]+)?)");
    private final String ffmpeg;
    private final ObjectMapper mapper;

    public AudioAnalysisService(@Value("${game-narrator.ffmpeg-command}") String ffmpeg, ObjectMapper mapper) {
        this.ffmpeg = ffmpeg;
        this.mapper = mapper;
    }

    public Path analyze(Path audio) {
        if (audio == null || !Files.isRegularFile(audio)) return null;
        Path output = audio.resolveSibling("audio-analysis.json");
        List<Map<String, Object>> windows = new ArrayList<>();
        List<Map<String, Object>> silence = new ArrayList<>();
        AtomicReference<Double> time = new AtomicReference<>(0.0);
        AtomicReference<Double> rms = new AtomicReference<>();
        AtomicReference<Double> silenceStart = new AtomicReference<>();
        try {
            String filter = "silencedetect=n=-45dB:d=0.6,asetnsamples=n=16000:p=1,astats=metadata=1:reset=1,ametadata=print";
            var result = ExternalProcessRunner.run(List.of(ffmpeg, "-nostdin", "-hide_banner", "-i", audio.toString(),
                    "-af", filter, "-f", "null", "-"), Duration.ofMinutes(30), null, line -> {
                Matcher timestamp = TIME.matcher(line);
                if (timestamp.find()) time.set(Double.parseDouble(timestamp.group(1)));
                Matcher start = SILENCE_START.matcher(line);
                if (start.find()) silenceStart.set(Double.parseDouble(start.group(1)));
                Matcher end = SILENCE_END.matcher(line);
                if (end.find() && silenceStart.get() != null)
                    silence.add(interval(silenceStart.getAndSet(null), Double.parseDouble(end.group(1)), "SILENCE"));
                Matcher value = VALUE.matcher(line);
                if (value.find()) {
                    double number = "-inf".equals(value.group(2)) ? -100.0 : Double.parseDouble(value.group(2));
                    if ("RMS_level".equals(value.group(1))) rms.set(number);
                    else if (rms.get() != null) {
                        double peak = number;
                        Map<String, Object> window = new LinkedHashMap<>();
                        window.put("startSeconds", Math.max(0, time.get()));
                        window.put("endSeconds", Math.max(0, time.get()) + 1.0);
                        window.put("rmsDb", rms.getAndSet(null));
                        window.put("peakDb", peak);
                        window.put("energyScore", energyScore((Double) window.get("rmsDb")));
                        window.put("peak", peak >= -6.0);
                        window.put("clipping", peak >= -1.0);
                        windows.add(window);
                    }
                }
            });
            if (result.exitCode() != 0) throw new IllegalStateException("FFmpeg exit " + result.exitCode());
            List<Map<String, Object>> peaks = windows.stream().filter(item -> Boolean.TRUE.equals(item.get("peak"))).toList();
            List<Map<String, Object>> clipping = windows.stream().filter(item -> Boolean.TRUE.equals(item.get("clipping"))).toList();
            AtomicArtifactWriter.writeJson(mapper, output, Map.of("schemaVersion", 1, "windowSeconds", 1,
                    "windows", windows, "silenceIntervals", silence, "peakIntervals", peaks,
                    "clippingIntervals", clipping));
            log.info("AUDIO_ANALYSIS_SUCCESS windows={} silence={} peaks={} clipping={} output={}",
                    windows.size(), silence.size(), peaks.size(), clipping.size(), output);
            return output;
        } catch (Exception exception) {
            log.warn("AUDIO_ANALYSIS_SKIPPED audio={} reason={}", audio, exception.getMessage());
            return null;
        }
    }

    private static Map<String, Object> interval(double start, double end, String type) {
        return Map.of("startSeconds", start, "endSeconds", end, "type", type);
    }

    static int energyScore(double rmsDb) {
        return (int) Math.round(Math.max(0, Math.min(100, (rmsDb + 55.0) * 2.0)));
    }
}
