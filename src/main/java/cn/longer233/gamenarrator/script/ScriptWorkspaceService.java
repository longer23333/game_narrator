package cn.longer233.gamenarrator.script;

import cn.longer233.gamenarrator.task.application.TaskNotFoundException;
import cn.longer233.gamenarrator.common.AtomicArtifactWriter;
import cn.longer233.gamenarrator.task.domain.VideoTask;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import cn.longer233.gamenarrator.voice.VoiceGenerator;
import cn.longer233.gamenarrator.voice.VoiceSegment;
import cn.longer233.gamenarrator.voice.VoiceOption;
import cn.longer233.gamenarrator.voice.VoiceRegenerationRequest;
import cn.longer233.gamenarrator.highlight.HighlightClip;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ScriptWorkspaceService {
    private final VideoTaskRepository repository;
    private final ObjectMapper objectMapper;
    private final OllamaScriptGenerator scriptGenerator;
    private final VoiceGenerator voiceGenerator;

    public ScriptWorkspaceService(VideoTaskRepository repository, ObjectMapper objectMapper,
                                  OllamaScriptGenerator scriptGenerator, VoiceGenerator voiceGenerator) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.scriptGenerator = scriptGenerator;
        this.voiceGenerator = voiceGenerator;
    }

    @Transactional
    public ScriptDocumentView find(UUID taskId) {
        return readDocument(requireTask(taskId));
    }

    @Transactional
    public ScriptDocumentView update(UUID taskId, int clipIndex, UpdateScriptSegmentRequest request) {
        VideoTask task = requireTask(taskId);
        ScriptDocumentView document = readDocument(task);
        ScriptSegment current = requireSegment(document.segments(), clipIndex);
        ScriptSegment replacement = new ScriptSegment(
                current.clipIndex(), current.startSeconds(), current.endSeconds(),
                request.narration().trim(),
                defaultText(request.subtitle(), request.narration()),
                defaultText(request.effectCue(), current.effectCue()));
        return saveRevision(task, document, replacement);
    }

    @Transactional
    public ScriptDocumentView regenerate(UUID taskId, int clipIndex,
                                         RegenerateScriptSegmentRequest request) {
        VideoTask task = requireTask(taskId);
        ScriptDocumentView document = readDocument(task);
        List<ScriptSegment> segments = document.segments();
        int position = positionOf(segments, clipIndex);
        List<HighlightClip> clips = readHighlightClips(task);
        if (position < clips.size() && clips.get(position).locked()) {
            throw new IllegalStateException("该分镜已锁定，请先取消锁定再使用 AI 重写");
        }
        ScriptSegment replacement = scriptGenerator.regenerateSegment(
                segments.get(position),
                request == null ? null : request.instruction(),
                position == 0 ? null : segments.get(position - 1).narration(),
                position + 1 >= segments.size() ? null : segments.get(position + 1).narration());
        return saveRevision(task, document, replacement);
    }

    @Transactional
    public VoiceSegment regenerateVoice(UUID taskId, int clipIndex, VoiceRegenerationRequest request) {
        VideoTask task = requireTask(taskId);
        Path scriptPath = requireScriptPath(task);
        requireSegment(readDocument(task).segments(), clipIndex);
        String voiceId = request == null ? null : request.voiceId();
        double speed = request == null ? 1.0 : request.effectiveSpeed();
        VoiceSegment result = voiceGenerator.regenerateSegment(scriptPath, clipIndex, voiceId, speed);
        Path manifest = scriptPath.getParent().resolve("voice-manifest.json");
        JsonNode manifestDocument = readJson(manifest);
        List<VoiceSegment> voices = readVoiceSegments(manifestDocument);
        validateVoiceManifest(readDocument(task).segments(), voices);
        task.applyVoiceRevision(manifest.toString(), voices.size());
        return result;
    }

    public List<VoiceOption> voiceOptions() {
        return voiceGenerator.options();
    }

    @Transactional
    public StoryboardView storyboard(UUID taskId) {
        VideoTask task = requireTask(taskId);
        ScriptDocumentView script = readDocument(task);
        List<HighlightClip> clips = readHighlightClips(task);
        if (script.segments().size() != clips.size()) {
            throw new IllegalStateException("分镜、文案片段数量不一致");
        }
        List<StoryboardSegmentView> segments = new ArrayList<>();
        for (int index = 0; index < clips.size(); index++) {
            HighlightClip clip = clips.get(index);
            ScriptSegment text = script.segments().get(index);
            segments.add(new StoryboardSegmentView(text.clipIndex(), clip.startSeconds(), clip.endSeconds(),
                    text.narration(), text.subtitle(), text.effectCue(), clip.eventType(),
                    clip.description(), clip.finalScore(), clip.locked(), clip.excluded()));
        }
        return new StoryboardView(script.title(), script.synopsis(), task.isStoryboardReviewEnabled(),
                task.isStoryboardApproved(), List.copyOf(segments));
    }

    @Transactional
    public StoryboardView updateStoryboard(UUID taskId, int clipIndex, UpdateStoryboardSegmentRequest request) {
        VideoTask task = requireTask(taskId);
        if (request.endSeconds() <= request.startSeconds()) {
            throw new IllegalArgumentException("分镜结束时间必须晚于开始时间");
        }
        if (task.getDurationSeconds() != null && request.endSeconds() > task.getDurationSeconds() + 0.001) {
            throw new IllegalArgumentException("分镜时间不能超过源视频时长");
        }
        ScriptDocumentView document = readDocument(task);
        ScriptSegment current = requireSegment(document.segments(), clipIndex);
        ScriptSegment replacement = new ScriptSegment(current.clipIndex(), request.startSeconds(), request.endSeconds(),
                request.narration().trim(), defaultText(request.subtitle(), request.narration()),
                defaultText(request.effectCue(), current.effectCue()));
        saveRevision(task, document, replacement);

        Path highlightPath = requireHighlightPath(task);
        JsonNode root = readJson(highlightPath);
        List<HighlightClip> clips = readHighlightClips(task);
        int position = positionOf(document.segments(), clipIndex);
        HighlightClip currentClip = clips.get(position);
        clips.set(position, new HighlightClip(currentClip.sourceFrameIndex(), request.startSeconds(), request.endSeconds(),
                Math.max(request.startSeconds(), Math.min(request.endSeconds(), currentClip.anchorSeconds())),
                currentClip.eventType(), currentClip.description(), currentClip.sourceScore(), currentClip.finalScore(),
                request.locked(), request.excluded()));
        Map<String, Object> updated = objectMapper.convertValue(root, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
        updated.put("clips", clips);
        updated.put("selectedDurationSeconds", clips.stream().mapToDouble(HighlightClip::durationSeconds).sum());
        writeAtomically(highlightPath, updated);
        return storyboard(taskId);
    }

    @Transactional
    public StoryboardView moveStoryboard(UUID taskId, int clipIndex, MoveStoryboardSegmentRequest request) {
        VideoTask task = requireTask(taskId);
        ScriptDocumentView document = readDocument(task);
        List<ScriptSegment> scripts = new ArrayList<>(document.segments());
        List<HighlightClip> clips = readHighlightClips(task);
        int from = positionOf(scripts, clipIndex);
        int to = "UP".equals(request.direction()) ? from - 1 : from + 1;
        if (to < 0 || to >= scripts.size()) return storyboard(taskId);
        java.util.Collections.swap(scripts, from, to);
        java.util.Collections.swap(clips, from, to);

        List<ScriptSegment> reindexed = new ArrayList<>();
        for (int index = 0; index < scripts.size(); index++) {
            ScriptSegment item = scripts.get(index);
            reindexed.add(new ScriptSegment(index + 1, item.startSeconds(), item.endSeconds(),
                    item.narration(), item.subtitle(), item.effectCue()));
        }
        String narration = String.join("\n", reindexed.stream().map(ScriptSegment::narration).toList());
        Path scriptPath = requireScriptPath(task);
        JsonNode scriptRoot = readJson(scriptPath);
        Map<String, Object> scriptOutput = objectMapper.convertValue(scriptRoot,
                new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
        scriptOutput.put("fullNarration", narration);
        scriptOutput.put("segments", reindexed);

        Path highlightPath = requireHighlightPath(task);
        Map<String, Object> highlightOutput = objectMapper.convertValue(readJson(highlightPath),
                new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
        highlightOutput.put("clips", clips);
        writeAtomically(highlightPath, highlightOutput);
        writeAtomically(scriptPath, scriptOutput);
        task.applyScriptRevision(document.title(), document.synopsis(), narration, scriptPath.toString(), reindexed.size());
        return storyboard(taskId);
    }

    @Transactional
    public Path storyboardThumbnail(UUID taskId, int clipIndex) {
        VideoTask task = requireTask(taskId);
        List<HighlightClip> clips = readHighlightClips(task);
        int position = positionOf(readDocument(task).segments(), clipIndex);
        int frameIndex = clips.get(position).sourceFrameIndex();
        JsonNode frames = readJson(Path.of(task.getVisualAnalysisPath())).path("frames");
        for (JsonNode frame : frames) {
            if (frame.path("index").asInt() == frameIndex) {
                Path image = Path.of(frame.path("imagePath").asText()).toAbsolutePath().normalize();
                Path ownedRoot = Path.of(task.getSourceVideoPath()).toAbsolutePath().normalize().getParent();
                if (ownedRoot == null || !image.startsWith(ownedRoot) || !Files.isRegularFile(image)) {
                    throw new IllegalStateException("分镜缩略图不存在或不属于该任务存储目录");
                }
                return image;
            }
        }
        throw new IllegalStateException("没有找到对应的分镜缩略图");
    }

    private ScriptDocumentView saveRevision(VideoTask task, ScriptDocumentView current,
                                            ScriptSegment replacement) {
        List<ScriptSegment> segments = new ArrayList<>(current.segments());
        int position = positionOf(segments, replacement.clipIndex());
        segments.set(position, replacement);
        String fullNarration = String.join("\n", segments.stream().map(ScriptSegment::narration).toList());
        ScriptDocumentView revised = new ScriptDocumentView(
                current.title(), current.synopsis(), fullNarration, current.qualityReview(), List.copyOf(segments));
        Path scriptPath = requireScriptPath(task);
        Map<String, Object> output = new LinkedHashMap<>();
        JsonNode existing = readJson(scriptPath);
        if (existing.hasNonNull("model")) output.put("model", existing.path("model").asText());
        output.put("title", revised.title());
        output.put("synopsis", revised.synopsis());
        output.put("fullNarration", revised.fullNarration());
        output.put("qualityReview", revised.qualityReview());
        output.put("segments", revised.segments());
        writeAtomically(scriptPath, output);
        task.applyScriptRevision(revised.title(), revised.synopsis(), revised.fullNarration(),
                scriptPath.toString(), revised.segments().size());
        return revised;
    }

    @Transactional
    public Map<String, Object> review(UUID taskId, int clipIndex, ManualScriptReviewRequest request) {
        VideoTask task = requireTask(taskId);
        ScriptDocumentView document = readDocument(task);
        requireSegment(document.segments(), clipIndex);
        Path path = requireScriptPath(task);
        JsonNode root = readJson(path);
        Map<String, Object> output = objectMapper.convertValue(root,
                new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
        Map<String, Object> reviews = root.path("manualReviews").isObject()
                ? objectMapper.convertValue(root.path("manualReviews"),
                    new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {})
                : new LinkedHashMap<>();
        reviews.put(Integer.toString(clipIndex), Map.of(
                "status", request.status(),
                "note", request.note() == null ? "" : request.note().trim(),
                "updatedAt", java.time.Instant.now().toString()));
        output.put("manualReviews", reviews);
        writeAtomically(path, output);
        return Map.copyOf(reviews);
    }

    @Transactional
    public Map<String, Object> reviews(UUID taskId) {
        JsonNode reviews = readJson(requireScriptPath(requireTask(taskId))).path("manualReviews");
        if (!reviews.isObject()) return Map.of();
        return objectMapper.convertValue(reviews,
                new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
    }

    private ScriptDocumentView readDocument(VideoTask task) {
        Path path = requireScriptPath(task);
        JsonNode document = readJson(path);
        try {
            List<ScriptSegment> segments = objectMapper.readerForListOf(ScriptSegment.class)
                    .readValue(document.path("segments"));
            if (segments.isEmpty()) throw new IllegalStateException("Script has no segments");
            return new ScriptDocumentView(
                    document.path("title").asText(),
                    document.path("synopsis").asText(),
                    document.path("fullNarration").asText(),
                    readQualityReview(document.path("qualityReview")),
                    List.copyOf(segments));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read script segments: " + exception.getMessage(), exception);
        }
    }

    private ScriptQualityReview readQualityReview(JsonNode node) {
        if (node == null || !node.isObject()) return ScriptQualityReview.unavailable();
        List<String> issues = new ArrayList<>();
        node.path("issues").forEach(issue -> { if (!issue.asText().isBlank()) issues.add(issue.asText()); });
        return new ScriptQualityReview(node.path("score").asInt(0), node.path("passed").asBoolean(false),
                List.copyOf(issues), node.path("summary").asText(""));
    }

    private List<VoiceSegment> readVoiceSegments(JsonNode document) {
        try {
            return objectMapper.readerForListOf(VoiceSegment.class).readValue(document.path("segments"));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read voice manifest: " + exception.getMessage(), exception);
        }
    }

    private void validateVoiceManifest(List<ScriptSegment> scripts, List<VoiceSegment> voices) {
        for (ScriptSegment script : scripts) {
            boolean matched = voices.stream().anyMatch(voice ->
                    voice.clipIndex() == script.clipIndex()
                            && script.narration().equals(voice.narration())
                            && Files.isRegularFile(Path.of(voice.audioPath())));
            if (!matched) {
                throw new IllegalStateException(
                        "Voice manifest is incomplete; regenerate the full voice stage first");
            }
        }
    }

    private VideoTask requireTask(UUID taskId) {
        return repository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
    }

    private Path requireHighlightPath(VideoTask task) {
        if (task.getHighlightManifestPath() == null) throw new IllegalStateException("任务尚未生成高光分镜");
        Path path = Path.of(task.getHighlightManifestPath()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) throw new IllegalStateException("高光分镜文件不存在");
        return path;
    }

    private List<HighlightClip> readHighlightClips(VideoTask task) {
        try {
            return new ArrayList<>(objectMapper.readerForListOf(HighlightClip.class)
                    .readValue(readJson(requireHighlightPath(task)).path("clips")));
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取高光分镜：" + exception.getMessage(), exception);
        }
    }

    private Path requireScriptPath(VideoTask task) {
        if (task.getGeneratedScriptPath() == null) {
            throw new IllegalStateException("Task has no generated script");
        }
        Path path = Path.of(task.getGeneratedScriptPath()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) throw new IllegalStateException("Generated script file is missing");
        return path;
    }

    private ScriptSegment requireSegment(List<ScriptSegment> segments, int clipIndex) {
        return segments.stream().filter(item -> item.clipIndex() == clipIndex).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Script segment does not exist: " + clipIndex));
    }

    private int positionOf(List<ScriptSegment> segments, int clipIndex) {
        for (int index = 0; index < segments.size(); index++) {
            if (segments.get(index).clipIndex() == clipIndex) return index;
        }
        throw new IllegalArgumentException("Script segment does not exist: " + clipIndex);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback.trim() : value.trim();
    }

    private JsonNode readJson(Path path) {
        try {
            return objectMapper.readTree(path.toFile());
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read JSON artifact: " + exception.getMessage(), exception);
        }
    }

    private void writeAtomically(Path target, Object value) {
        try {
            AtomicArtifactWriter.writeJson(objectMapper, target, value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot save script revision: " + exception.getMessage(), exception);
        }
    }
}
