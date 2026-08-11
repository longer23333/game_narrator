package cn.longer233.gamenarrator.script;

import cn.longer233.gamenarrator.task.application.TaskNotFoundException;
import cn.longer233.gamenarrator.common.AtomicArtifactWriter;
import cn.longer233.gamenarrator.task.domain.VideoTask;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import cn.longer233.gamenarrator.voice.VoiceSynthesizer;
import cn.longer233.gamenarrator.voice.VoiceSegment;
import cn.longer233.gamenarrator.voice.VoiceOption;
import cn.longer233.gamenarrator.voice.VoiceRegenerationRequest;
import cn.longer233.gamenarrator.highlight.HighlightClip;
import cn.longer233.gamenarrator.event.NarrativeBeat;
import cn.longer233.gamenarrator.personalization.DirectorProfileService;
import cn.longer233.gamenarrator.personalization.DirectorProfileView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class ScriptWorkspaceService {
    private final VideoTaskRepository repository;
    private final ObjectMapper objectMapper;
    private final TextGenerator scriptGenerator;
    private final VoiceSynthesizer voiceGenerator;
    private final DirectorProfileService directorProfiles;

    public ScriptWorkspaceService(VideoTaskRepository repository, ObjectMapper objectMapper,
                                  TextGenerator scriptGenerator, VoiceSynthesizer voiceGenerator) {
        this(repository, objectMapper, scriptGenerator, voiceGenerator, null);
    }

    @Autowired
    public ScriptWorkspaceService(VideoTaskRepository repository, ObjectMapper objectMapper,
                                  TextGenerator scriptGenerator, VoiceSynthesizer voiceGenerator,
                                  DirectorProfileService directorProfiles) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.scriptGenerator = scriptGenerator;
        this.voiceGenerator = voiceGenerator;
        this.directorProfiles = directorProfiles;
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
        ScriptDocumentView result = saveRevision(task, document, replacement);
        if (directorProfiles != null) directorProfiles.recordScriptEdit(taskId, current, replacement);
        return result;
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
        String instruction = request == null ? null : request.instruction();
        if (instruction == null || instruction.isBlank()) {
            instruction = feedbackInstruction(task, document, clipIndex);
        }
        ScriptSegment replacement = scriptGenerator.regenerateSegment(
                segments.get(position),
                instruction,
                position == 0 ? null : segments.get(position - 1).narration(),
                position + 1 >= segments.size() ? null : segments.get(position + 1).narration());
        return saveRevision(task, document, replacement);
    }

    @Transactional
    public ScriptDocumentView qualityReview(UUID taskId) {
        VideoTask task = requireTask(taskId);
        ScriptDocumentView document = readDocument(task);
        Path path = requireScriptPath(task);
        JsonNode root = readJson(path);
        Map<String, Object> manualReviews = root.path("manualReviews").isObject()
                ? objectMapper.convertValue(root.path("manualReviews"),
                    new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {})
                : Map.of();
        ScriptQualityReview review = scriptGenerator.reviewQuality(document, manualReviews);
        Map<String, Object> output = objectMapper.convertValue(root,
                new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
        output.put("qualityReview", review);
        output.put("qualityReviewSource", "AI_INDEPENDENT_REVIEW");
        output.put("qualityReviewedAt", java.time.Instant.now().toString());
        writeAtomically(path, output);
        return new ScriptDocumentView(document.title(), document.synopsis(), document.fullNarration(), review,
                document.segments());
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
        StoryboardSegmentView aiSuggestion = new StoryboardSegmentView(current.clipIndex(), currentClip.startSeconds(),
                currentClip.endSeconds(), current.narration(), current.subtitle(), current.effectCue(),
                currentClip.eventType(), currentClip.description(), currentClip.finalScore(),
                currentClip.locked(), currentClip.excluded());
        HighlightClip changedClip = new HighlightClip(currentClip.sourceFrameIndex(), request.startSeconds(), request.endSeconds(),
                Math.max(request.startSeconds(), Math.min(request.endSeconds(), currentClip.anchorSeconds())),
                currentClip.eventType(), currentClip.description(), currentClip.sourceScore(), currentClip.finalScore(),
                request.locked(), request.excluded());
        clips.set(position, changedClip);
        Map<String, Object> updated = objectMapper.convertValue(root, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
        updated.put("clips", clips);
        List<HighlightClip> manualDecisions = mergeManualDecisions(root, changedClip);
        updated.put("manualDecisions", manualDecisions);
        updated.put("manualDecisionCount", manualDecisions.size());
        updated.put("selectedDurationSeconds", clips.stream().filter(clip -> !clip.excluded())
                .mapToDouble(HighlightClip::durationSeconds).sum());
        writeAtomically(highlightPath, updated);
        StoryboardView result = storyboard(taskId);
        if (directorProfiles != null) {
            StoryboardSegmentView finalValue = result.segments().stream()
                    .filter(item -> item.clipIndex() == clipIndex).findFirst().orElseThrow();
            directorProfiles.recordStoryboardEdit(taskId, aiSuggestion, finalValue);
        }
        return result;
    }

    private List<HighlightClip> mergeManualDecisions(JsonNode manifest, HighlightClip changedClip) {
        JsonNode source = manifest.path("manualDecisions").isArray()
                ? manifest.path("manualDecisions") : manifest.path("clips");
        List<HighlightClip> saved;
        try {
            saved = objectMapper.readerForListOf(HighlightClip.class).readValue(source);
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取人工高光决定：" + exception.getMessage(), exception);
        }
        Map<Integer, HighlightClip> byFrame = saved.stream()
                .filter(clip -> clip.locked() || clip.excluded())
                .collect(java.util.stream.Collectors.toMap(HighlightClip::sourceFrameIndex,
                        clip -> clip, (first, replacement) -> replacement, LinkedHashMap::new));
        byFrame.remove(changedClip.sourceFrameIndex());
        if (changedClip.locked() || changedClip.excluded()) {
            byFrame.put(changedClip.sourceFrameIndex(), changedClip);
        }
        return List.copyOf(byFrame.values());
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
    public StoryboardView applyNarrativeStructure(UUID taskId, List<NarrativeBeat> beats) {
        VideoTask task = requireTask(taskId);
        ScriptDocumentView document = readDocument(task);
        List<ScriptSegment> scripts = new ArrayList<>(document.segments());
        List<HighlightClip> clips = readHighlightClips(task);
        DirectorProfileView director = directorProfiles == null ? null : directorProfiles.profile();
        Map<Integer, NarrativeBeat> beatByClip = new LinkedHashMap<>();
        beats.stream().filter(beat -> beat.clipIndex() != null)
                .forEach(beat -> beatByClip.putIfAbsent(beat.clipIndex(), beat));
        List<Integer> order = new ArrayList<>(beatByClip.keySet());
        scripts.stream().map(ScriptSegment::clipIndex).filter(index -> !order.contains(index)).forEach(order::add);
        Map<Integer, ScriptSegment> scriptsByIndex = new LinkedHashMap<>();
        Map<Integer, HighlightClip> clipsByIndex = new LinkedHashMap<>();
        for (int index = 0; index < scripts.size(); index++) {
            scriptsByIndex.put(scripts.get(index).clipIndex(), scripts.get(index));
            clipsByIndex.put(scripts.get(index).clipIndex(), clips.get(index));
        }
        List<ScriptSegment> reorderedScripts = new ArrayList<>();
        List<HighlightClip> reorderedClips = new ArrayList<>();
        for (int position = 0; position < order.size(); position++) {
            int originalIndex = order.get(position);
            ScriptSegment script = scriptsByIndex.get(originalIndex);
            HighlightClip clip = clipsByIndex.get(originalIndex);
            NarrativeBeat beat = beatByClip.get(originalIndex);
            if (beat != null) {
                double durationPreference = director == null ? 1 : director.preferredDurationRatio();
                clip = pacedClip(clip, beat.paceMultiplier() / durationPreference, task.getDurationSeconds());
                String preferredEffect = director == null || director.preferredEffects().isEmpty()
                        ? script.effectCue() : defaultText(script.effectCue(), director.preferredEffects().getFirst());
                String cue = narrativeCue(preferredEffect, beat);
                boolean concise = director != null && director.preferredTextDensityRatio() < .90;
                String narration = concise ? script.narration() : narrativeNarration(script.narration(), beat);
                String subtitle = concise ? script.subtitle() : narrativeNarration(script.subtitle(), beat);
                script = new ScriptSegment(position + 1, clip.startSeconds(), clip.endSeconds(),
                        narration, subtitle, cue);
            } else {
                script = new ScriptSegment(position + 1, script.startSeconds(), script.endSeconds(),
                        script.narration(), script.subtitle(), script.effectCue());
            }
            reorderedScripts.add(script);
            reorderedClips.add(clip);
        }
        Path scriptPath = requireScriptPath(task);
        Map<String, Object> scriptOutput = objectMapper.convertValue(readJson(scriptPath),
                new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
        String narration = String.join("\n", reorderedScripts.stream().map(ScriptSegment::narration).toList());
        scriptOutput.put("fullNarration", narration);
        scriptOutput.put("segments", reorderedScripts);
        Path highlightPath = requireHighlightPath(task);
        Map<String, Object> highlightOutput = objectMapper.convertValue(readJson(highlightPath),
                new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
        highlightOutput.put("clips", reorderedClips);
        highlightOutput.put("selectedDurationSeconds", reorderedClips.stream()
                .filter(item -> !item.excluded()).mapToDouble(HighlightClip::durationSeconds).sum());
        writeAtomically(highlightPath, highlightOutput);
        writeAtomically(scriptPath, scriptOutput);
        task.applyScriptRevision(document.title(), document.synopsis(), narration,
                scriptPath.toString(), reorderedScripts.size());
        return storyboard(taskId);
    }

    private HighlightClip pacedClip(HighlightClip clip, double pace, Double sourceDuration) {
        if (clip.locked()) return clip;
        double duration = Math.max(1.5, clip.durationSeconds() / Math.max(.6, pace));
        double start = Math.max(0, clip.anchorSeconds() - duration / 2);
        double end = start + duration;
        if (sourceDuration != null && end > sourceDuration) {
            end = sourceDuration;
            start = Math.max(0, end - duration);
        }
        return new HighlightClip(clip.sourceFrameIndex(), start, end,
                Math.max(start, Math.min(end, clip.anchorSeconds())), clip.eventType(), clip.description(),
                clip.sourceScore(), clip.finalScore(), clip.locked(), clip.excluded());
    }

    private String narrativeCue(String existing, NarrativeBeat beat) {
        String marker = String.format(java.util.Locale.ROOT, "NARRATIVE_%s pace=%.2f music=%.2f",
                beat.stage(), beat.paceMultiplier(), beat.musicIntensity());
        return existing == null || existing.isBlank() ? marker : marker + "; " + existing;
    }

    private String narrativeNarration(String existing, NarrativeBeat beat) {
        String bridge = switch (beat.stage()) {
            case SETUP -> "先看局势如何展开。";
            case CRISIS -> "危险正在逼近。";
            case REVERSAL -> "战局从这里发生变化。";
            case CLIMAX -> "关键时刻到了。";
            case RESULT -> "最后回看这场战斗的结果。";
        };
        String safe = existing == null ? "" : existing.trim();
        return safe.startsWith(bridge) ? safe : bridge + safe;
    }

    /** Applies the visual editor's ordered source ranges to the renderable storyboard artifacts. */
    @Transactional
    public void applyEditorTimeline(UUID taskId, JsonNode timeline) {
        VideoTask task = requireTask(taskId);
        ScriptDocumentView document = readDocument(task);
        List<HighlightClip> currentClips = readHighlightClips(task);
        List<JsonNode> ordered = new ArrayList<>();
        timeline.path("clips").forEach(ordered::add);
        ordered.sort(java.util.Comparator.comparingDouble(item -> item.path("timelineStartSeconds").asDouble()));
        if (ordered.isEmpty()) throw new IllegalArgumentException("时间线至少需要保留一个片段");
        List<ScriptSegment> scripts = new ArrayList<>();
        List<HighlightClip> highlights = new ArrayList<>();
        for (int position = 0; position < ordered.size(); position++) {
            JsonNode editorClip = ordered.get(position);
            String id = editorClip.path("id").asText();
            List<Integer> rawSourcePositions = new ArrayList<>();
            if (editorClip.path("sourceClipIndexes").isArray()) {
                editorClip.path("sourceClipIndexes").forEach(item -> rawSourcePositions.add(item.asInt() - 1));
            } else rawSourcePositions.add(editorClip.has("sourceClipIndex")
                    ? editorClip.path("sourceClipIndex").asInt() - 1
                    : id.startsWith("clip-") ? Integer.parseInt(id.substring(5)) - 1 : position);
            List<Integer> sourcePositions = rawSourcePositions.stream()
                    .map(source -> Math.max(0, Math.min(document.segments().size() - 1, source)))
                    .toList();
            int sourcePosition = sourcePositions.getFirst();
            double start = editorClip.path("sourceStartSeconds").asDouble();
            double end = editorClip.path("sourceEndSeconds").asDouble();
            if (end <= start + .04 || start < 0 || (task.getDurationSeconds() != null && end > task.getDurationSeconds() + .001)) {
                throw new IllegalArgumentException("可视时间线入点或出点无效");
            }
            List<ScriptSegment> mergedTexts = sourcePositions.stream().map(document.segments()::get).toList();
            scripts.add(new ScriptSegment(position + 1, start, end,
                    joinDistinct(mergedTexts.stream().map(ScriptSegment::narration).toList(), "\n"),
                    joinDistinct(mergedTexts.stream().map(ScriptSegment::subtitle).toList(), " "),
                    joinDistinct(mergedTexts.stream().map(ScriptSegment::effectCue).toList(), "；")));
            HighlightClip clip = currentClips.get(sourcePosition);
            highlights.add(new HighlightClip(clip.sourceFrameIndex(), start, end,
                    Math.max(start, Math.min(end, clip.anchorSeconds())), clip.eventType(), clip.description(),
                    clip.sourceScore(), clip.finalScore(), clip.locked(), clip.excluded()));
            ((com.fasterxml.jackson.databind.node.ObjectNode) editorClip).put("id", "clip-" + (position + 1));
            ((com.fasterxml.jackson.databind.node.ObjectNode) editorClip).put("sourceClipIndex", position + 1);
            ((com.fasterxml.jackson.databind.node.ObjectNode) editorClip).remove("sourceClipIndexes");
        }
        String narration = String.join("\n", scripts.stream().map(ScriptSegment::narration).toList());
        Path scriptPath = requireScriptPath(task);
        Map<String, Object> scriptOutput = objectMapper.convertValue(readJson(scriptPath),
                new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
        scriptOutput.put("fullNarration", narration); scriptOutput.put("segments", scripts);
        Path highlightPath = requireHighlightPath(task);
        Map<String, Object> highlightOutput = objectMapper.convertValue(readJson(highlightPath),
                new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
        highlightOutput.put("clips", highlights);
        highlightOutput.put("selectedDurationSeconds", highlights.stream().mapToDouble(HighlightClip::durationSeconds).sum());
        writeAtomically(highlightPath, highlightOutput); writeAtomically(scriptPath, scriptOutput);
        task.applyScriptRevision(document.title(), document.synopsis(), narration, scriptPath.toString(), scripts.size());
    }

    private String joinDistinct(List<String> values, String delimiter) {
        return String.join(delimiter, values.stream().filter(Objects::nonNull).map(String::trim)
                .filter(value -> !value.isBlank()).distinct().toList());
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
                current.title(), current.synopsis(), fullNarration, ScriptQualityReview.stale(), List.copyOf(segments));
        Path scriptPath = requireScriptPath(task);
        JsonNode existing = readJson(scriptPath);
        Map<String, Object> output = objectMapper.convertValue(existing,
                new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
        output.put("title", revised.title());
        output.put("synopsis", revised.synopsis());
        output.put("fullNarration", revised.fullNarration());
        output.put("qualityReview", revised.qualityReview());
        output.remove("qualityReviewSource");
        output.remove("qualityReviewedAt");
        output.put("segments", revised.segments());
        writeAtomically(scriptPath, output);
        task.applyScriptRevision(revised.title(), revised.synopsis(), revised.fullNarration(),
                scriptPath.toString(), revised.segments().size());
        return revised;
    }

    private String feedbackInstruction(VideoTask task, ScriptDocumentView document, int clipIndex) {
        JsonNode root = readJson(requireScriptPath(task));
        List<String> feedback = new ArrayList<>();
        JsonNode manual = root.path("manualReviews").path(Integer.toString(clipIndex));
        if ("NEEDS_CHANGES".equals(manual.path("status").asText()) && !manual.path("note").asText().isBlank()) {
            feedback.add("人工评审：" + manual.path("note").asText().trim());
        }
        String marker = Integer.toString(clipIndex);
        document.qualityReview().issues().stream()
                .filter(issue -> issue.matches("(?is).*(?:片段|分镜|clip)\\s*" + marker + "(?:\\D.*|$)"))
                .forEach(issue -> feedback.add("AI 评审：" + issue));
        return feedback.isEmpty() ? "提高连贯性、事实一致性、可配音性和字幕精炼度"
                : String.join("；", feedback);
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
