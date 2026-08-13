package cn.longer233.gamenarrator.timeline;

import cn.longer233.gamenarrator.highlight.HighlightClip;
import cn.longer233.gamenarrator.common.AtomicArtifactWriter;
import cn.longer233.gamenarrator.script.ScriptSegment;
import cn.longer233.gamenarrator.voice.VoiceSegment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sound.sampled.AudioSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TimelinePlanner {
    private static final Logger log = LoggerFactory.getLogger(TimelinePlanner.class);
    private final ObjectMapper objectMapper;
    private final TimelineValidator timelineValidator;
    private final TimelineTransitionPlanner transitionPlanner;

    @Autowired
    public TimelinePlanner(ObjectMapper objectMapper, TimelineValidator timelineValidator,
                           TimelineTransitionPlanner transitionPlanner) {
        this.objectMapper = objectMapper;
        this.timelineValidator = timelineValidator;
        this.transitionPlanner = transitionPlanner;
    }

    public TimelinePlanner(ObjectMapper objectMapper, TimelineValidator timelineValidator) {
        this(objectMapper, timelineValidator, new TimelineTransitionPlanner());
    }

    public TimelinePlanningResult plan(Path highlightPath, Path scriptPath, Path voiceManifestPath) {
        try {
            List<HighlightClip> clips = readList(highlightPath, "clips", HighlightClip.class);
            List<ScriptSegment> scripts = readList(scriptPath, "segments", ScriptSegment.class);
            List<VoiceSegment> voices = readList(voiceManifestPath, "segments", VoiceSegment.class);
            if (clips.isEmpty()) throw new IllegalStateException("高光清单为空，无法规划时间线");
            if (clips.size() != scripts.size() || clips.size() != voices.size()) {
                throw new IllegalStateException("高光、文案和配音的片段数量不一致");
            }
            log.info("TIMELINE_PLANNING_BEGIN segmentCount={}", clips.size());
            List<TimelineSegment> timeline = new ArrayList<>();
            double cursor = 0;
            int overflowCount = 0;
            int sequence = 1;
            for (int index = 0; index < clips.size(); index++) {
                HighlightClip clip = clips.get(index);
                if (clip.excluded()) continue;
                ScriptSegment script = scripts.get(index);
                VoiceSegment voice = voices.get(index);
                double clipDuration = clip.endSeconds() - clip.startSeconds();
                double voiceDuration = wavDuration(Path.of(voice.audioPath()));
                boolean overflow = voiceDuration > clipDuration - 0.3;
                if (overflow) overflowCount++;
                double previousDuration = timeline.isEmpty() ? 0
                        : timeline.getLast().sourceEndSeconds() - timeline.getLast().sourceStartSeconds();
                var transition = transitionPlanner.boundary(script.effectCue(), sequence, previousDuration, clipDuration);
                cursor = Math.max(0, cursor - transition.durationSeconds());
                timeline.add(new TimelineSegment(sequence++, cursor, cursor + clipDuration,
                        clip.startSeconds(), clip.endSeconds(), script.narration(), script.subtitle(),
                        script.effectCue(), voice.audioPath(), voiceDuration, overflow, transition.type(),
                        transition.durationSeconds(), transition.direction(), transition.curve()));
                cursor += clipDuration;
            }
            Path output = highlightPath.getParent().resolve("timeline.json");
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("version", 1);
            document.put("outputDurationSeconds", cursor);
            document.put("voiceOverflowCount", overflowCount);
            document.put("segments", timeline);
            timelineValidator.validate(timeline, cursor);
            AtomicArtifactWriter.writeJson(objectMapper, output, document);
            log.info("TIMELINE_PLANNING_SUCCESS segmentCount={} outputDuration={} overflowCount={} output={}",
                    timeline.size(), cursor, overflowCount, output);
            return new TimelinePlanningResult(output.toString(), cursor, overflowCount, timeline);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("时间线规划失败：" + exception.getMessage(), exception);
        }
    }

    public TimelinePlanningResult refreshSegment(Path timelinePath, Path highlightPath, Path scriptPath,
            Path voiceManifestPath, int clipIndex) {
        try {
            JsonNode existing = objectMapper.readTree(timelinePath.toFile());
            List<TimelineSegment> timeline = objectMapper.readerForListOf(TimelineSegment.class)
                    .readValue(existing.path("segments"));
            List<ScriptSegment> scripts = readList(scriptPath, "segments", ScriptSegment.class);
            List<HighlightClip> clips = readList(highlightPath, "clips", HighlightClip.class);
            List<VoiceSegment> voices = readList(voiceManifestPath, "segments", VoiceSegment.class);
            ScriptSegment script = scripts.stream().filter(item -> item.clipIndex() == clipIndex).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Script segment does not exist: " + clipIndex));
            VoiceSegment voice = voices.stream().filter(item -> item.clipIndex() == clipIndex).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Voice segment does not exist: " + clipIndex));
            int scriptPosition = -1;
            for (int index = 0; index < scripts.size(); index++) {
                if (scripts.get(index).clipIndex() == clipIndex) { scriptPosition = index; break; }
            }
            if (scriptPosition < 0 || scriptPosition >= clips.size())
                throw new IllegalStateException("Highlight list does not contain script segment: " + clipIndex);
            if (clips.get(scriptPosition).excluded())
                throw new IllegalStateException("Excluded segment has no timeline entry: " + clipIndex);
            int position = 0;
            for (int index = 0; index < scriptPosition; index++) if (!clips.get(index).excluded()) position++;
            if (position >= timeline.size())
                throw new IllegalStateException("Timeline does not contain script segment: " + clipIndex);
            TimelineSegment current = timeline.get(position);
            double voiceDuration = wavDuration(Path.of(voice.audioPath()));
            double clipDuration = current.outputEndSeconds() - current.outputStartSeconds();
            TimelineSegment refreshed = new TimelineSegment(current.sequence(), current.outputStartSeconds(),
                    current.outputEndSeconds(), current.sourceStartSeconds(), current.sourceEndSeconds(),
                    script.narration(), script.subtitle(), script.effectCue(), voice.audioPath(), voiceDuration,
                    voiceDuration > clipDuration - 0.3, current.transitionType(),
                    current.transitionDurationSeconds(), current.transitionDirection(), current.transitionCurve());
            List<TimelineSegment> revised = new ArrayList<>(timeline);
            revised.set(position, refreshed);
            double outputDuration = existing.path("outputDurationSeconds").asDouble(
                    revised.getLast().outputEndSeconds());
            int overflowCount = (int) revised.stream().filter(TimelineSegment::voiceOverflow).count();
            timelineValidator.validate(revised, outputDuration);
            Map<String, Object> document = objectMapper.convertValue(existing,
                    new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() { });
            document.put("outputDurationSeconds", outputDuration);
            document.put("voiceOverflowCount", overflowCount);
            document.put("segments", revised);
            document.put("localizedRevision", Map.of("clipIndex", clipIndex,
                    "updatedAt", java.time.Instant.now().toString()));
            AtomicArtifactWriter.writeJson(objectMapper, timelinePath, document);
            return new TimelinePlanningResult(timelinePath.toString(), outputDuration, overflowCount,
                    List.copyOf(revised));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Local timeline refresh failed: " + exception.getMessage(), exception);
        }
    }

    private <T> List<T> readList(Path path, String field, Class<T> type) throws Exception {
        JsonNode root = objectMapper.readTree(path.toFile());
        return objectMapper.readerForListOf(type).readValue(root.path(field));
    }

    private double wavDuration(Path path) throws Exception {
        if (!Files.isRegularFile(path)) throw new IllegalStateException("配音文件不存在：" + path);
        try (var stream = AudioSystem.getAudioInputStream(path.toFile())) {
            long frames = stream.getFrameLength();
            float rate = stream.getFormat().getFrameRate();
            if (frames <= 0 || rate <= 0) throw new IllegalStateException("无法读取配音时长：" + path);
            return frames / rate;
        }
    }
}
