package cn.longer233.gamenarrator.event;

import cn.longer233.gamenarrator.script.GeneratedScript;
import cn.longer233.gamenarrator.script.TextGenerator;
import cn.longer233.gamenarrator.script.ScriptDocumentView;
import cn.longer233.gamenarrator.script.ScriptQualityReview;
import cn.longer233.gamenarrator.pipeline.TaskArtifactLocator;
import cn.longer233.gamenarrator.task.application.TaskNotFoundException;
import cn.longer233.gamenarrator.task.domain.VideoTask;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ConfirmedEventScriptService {
    private final VideoTaskRepository tasks;
    private final GameEventTimelineService events;
    private final TextGenerator generator;
    private final ObjectMapper objectMapper;
    private final TaskArtifactLocator artifacts;

    public ConfirmedEventScriptService(VideoTaskRepository tasks, GameEventTimelineService events,
                                       TextGenerator generator, ObjectMapper objectMapper,
                                       TaskArtifactLocator artifacts) {
        this.tasks = tasks;
        this.events = events;
        this.generator = generator;
        this.objectMapper = objectMapper;
        this.artifacts = artifacts;
    }

    public ScriptDocumentView regenerate(UUID taskId) {
        VideoTask task = tasks.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
        List<GameEventFact> facts = events.confirmedFacts(taskId);
        if (facts.isEmpty()) throw new IllegalStateException("请先确认至少一个游戏事件，再生成事实约束文案");
        Path highlights = artifacts.latest(taskId, "HIGHLIGHT_MANIFEST")
                .orElseThrow(() -> new IllegalStateException("任务尚未生成分镜"));
        GeneratedScript result = generator.generate(highlights, task.getGameCategory(),
                task.getCommentaryStyle().name(), task.getTaskBrief(), task.getTranscriptText(), facts);
        task.applyScriptRevision(result.title(), result.synopsis(), result.fullNarration(),
                result.scriptPath(), result.segments().size());
        tasks.save(task);
        return read(result);
    }

    private ScriptDocumentView read(GeneratedScript script) {
        try {
            JsonNode root = objectMapper.readTree(Path.of(script.scriptPath()).toFile());
            JsonNode review = root.path("qualityReview");
            List<String> issues = new ArrayList<>();
            review.path("issues").forEach(item -> issues.add(item.asText()));
            ScriptQualityReview quality = review.isObject()
                    ? new ScriptQualityReview(review.path("score").asInt(), review.path("passed").asBoolean(),
                        List.copyOf(issues), review.path("summary").asText())
                    : ScriptQualityReview.unavailable();
            return new ScriptDocumentView(script.title(), script.synopsis(), script.fullNarration(), quality,
                    script.segments());
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取事实约束文案", exception);
        }
    }
}
