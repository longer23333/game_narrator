package cn.longer233.gamenarrator.editor;

import cn.longer233.gamenarrator.script.ScriptWorkspaceService;
import cn.longer233.gamenarrator.identity.CurrentUserContext;
import cn.longer233.gamenarrator.script.StoryboardSegmentView;
import cn.longer233.gamenarrator.task.application.TaskNotFoundException;
import cn.longer233.gamenarrator.task.domain.VideoTask;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.transaction.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sound.sampled.AudioSystem;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class EditorTimelineService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final VideoTaskRepository tasks;
    private final ScriptWorkspaceService workspace;
    private final CurrentUserContext currentUser;

    public EditorTimelineService(JdbcTemplate jdbc, ObjectMapper mapper, VideoTaskRepository tasks,
                                 ScriptWorkspaceService workspace, CurrentUserContext currentUser) {
        this.jdbc = jdbc; this.mapper = mapper; this.tasks = tasks; this.workspace = workspace;
        this.currentUser = currentUser;
    }

    @Transactional
    public JsonNode timeline(UUID taskId) {
        ObjectNode result = timelineFrom(currentManifest(taskId), taskId);
        attachHistory(result, taskId);
        return result;
    }

    @Transactional
    public JsonNode checkout(UUID taskId, UUID revisionId) {
        requireTask(taskId);
        jdbc.update("UPDATE video_project SET current_revision_id=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",
                revisionId, taskId);
        ObjectNode result = timelineFrom(currentManifest(taskId), taskId);
        syncRenderableStoryboard(taskId, result);
        attachHistory(result, taskId);
        return result;
    }

    @Transactional
    public JsonNode command(UUID taskId, EditorCommandRequest request) {
        String type = request.type().trim().toUpperCase(Locale.ROOT);
        if ("UNDO".equals(type)) return undo(taskId);
        if ("REDO".equals(type)) return redo(taskId);
        ObjectNode manifest = currentManifest(taskId);
        ObjectNode timeline = timelineFrom(manifest, taskId);
        Map<String, Object> values = request.values();
        switch (type) {
            case "SPLIT" -> split(timeline, text(values, "clipId"), number(values, "atSeconds"));
            case "MERGE" -> mergeWithNext(timeline, text(values, "clipId"));
            case "DELETE" -> delete(timeline, text(values, "clipId"));
            case "MOVE" -> move(timeline, text(values, "clipId"), text(values, "trackId"),
                    number(values, "timelineStartSeconds"), bool(values, "snap", true));
            case "TRIM" -> trim(timeline, text(values, "clipId"), number(values, "sourceStartSeconds"),
                    number(values, "sourceEndSeconds"));
            case "TRACK_STATE" -> trackState(timeline, text(values, "trackId"),
                    bool(values, "muted", false), bool(values, "solo", false));
            case "KEYFRAME_SET" -> keyframe(timeline, text(values, "clipId"), text(values, "property"),
                    number(values, "timeSeconds"), number(values, "value"));
            case "COLOR_SET" -> color(timeline, text(values, "clipId"), values);
            default -> throw new IllegalArgumentException("不支持的剪辑命令：" + type);
        }
        if (Set.of("SPLIT", "MERGE", "DELETE", "MOVE", "TRIM").contains(type)) normalizeClipOrder(timeline);
        if (Set.of("SPLIT", "MERGE", "DELETE", "MOVE", "TRIM").contains(type)) syncRenderableStoryboard(taskId, timeline);
        manifest.set("editorTimeline", timeline);
        saveRevision(taskId, manifest, type, "手动剪辑：" + type);
        attachHistory(timeline, taskId);
        return timeline;
    }

    @Transactional
    public Map<String, Object> waveform(UUID taskId, int points) {
        VideoTask task = requireTask(taskId);
        int target = Math.max(64, Math.min(4096, points));
        if (task.getExtractedAudioPath() == null) return Map.of("points", List.of(), "available", false);
        Path audio = Path.of(task.getExtractedAudioPath()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(audio)) return Map.of("points", List.of(), "available", false);
        try (var input = AudioSystem.getAudioInputStream(audio.toFile())) {
            int frameSize = Math.max(1, input.getFormat().getFrameSize());
            long expectedFrames = input.getFrameLength();
            if (expectedFrames <= 0) expectedFrames = Math.max(1, Files.size(audio) / frameSize);
            long bucket = Math.max(1, (expectedFrames + target - 1) / target);
            byte[] buffer = new byte[Math.max(frameSize, (64 * 1024 / frameSize) * frameSize)];
            List<Double> peaks = new ArrayList<>(target);
            long frames = 0, bucketFrames = 0;
            double peak = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                for (int offset = 0; offset + frameSize <= read; offset += frameSize) {
                    int sample = frameSize >= 2
                            ? (short) ((buffer[offset] & 0xff) | (buffer[offset + 1] << 8))
                            : (buffer[offset] - 128) << 8;
                    peak = Math.max(peak, Math.abs(sample) / 32768.0);
                    frames++;
                    if (++bucketFrames >= bucket) {
                        peaks.add(Math.round(peak * 1000.0) / 1000.0);
                        peak = 0;
                        bucketFrames = 0;
                    }
                }
            }
            if (bucketFrames > 0) peaks.add(Math.round(peak * 1000.0) / 1000.0);
            return Map.of("points", peaks, "available", true,
                    "durationSeconds", frames / input.getFormat().getFrameRate());
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成音频波形：" + exception.getMessage(), exception);
        }
    }

    private ObjectNode timelineFrom(ObjectNode manifest, UUID taskId) {
        if (manifest.path("editorTimeline").isObject()) return (ObjectNode) manifest.path("editorTimeline").deepCopy();
        ObjectNode timeline = mapper.createObjectNode(); timeline.put("version", 1); timeline.put("snapSeconds", .15);
        ArrayNode tracks = timeline.putArray("tracks");
        addTrack(tracks, "video-1", "VIDEO", "主视频", 0);
        addTrack(tracks, "overlay-1", "OVERLAY", "叠加", 1);
        addTrack(tracks, "audio-1", "AUDIO", "原声/配音", 2);
        addTrack(tracks, "subtitle-1", "SUBTITLE", "字幕", 3);
        ArrayNode clips = timeline.putArray("clips"); double cursor = 0;
        VideoTask sourceTask = requireTask(taskId);
        List<StoryboardSegmentView> segments = List.of();
        if (sourceTask.getGeneratedScriptPath() != null && sourceTask.getHighlightManifestPath() != null) {
            segments = workspace.storyboard(taskId).segments();
        }
        for (StoryboardSegmentView segment : segments) {
            ObjectNode clip = clips.addObject();
            clip.put("id", "clip-" + segment.clipIndex()); clip.put("trackId", "video-1");
            clip.put("sourceClipIndex", segment.clipIndex());
            clip.put("sourceStartSeconds", segment.startSeconds()); clip.put("sourceEndSeconds", segment.endSeconds());
            clip.put("timelineStartSeconds", cursor); clip.put("durationSeconds", segment.endSeconds() - segment.startSeconds());
            clip.put("sourceVolume", 1.0); clip.put("muted", false); clip.putObject("color")
                    .put("brightness", 0).put("contrast", 1).put("saturation", 1).put("temperature", 0);
            clip.putArray("keyframes"); cursor += segment.endSeconds() - segment.startSeconds();
        }
        if (clips.isEmpty()) {
            double duration = sourceTask.getDurationSeconds() == null
                    ? sourceTask.getTargetDurationSeconds() : sourceTask.getDurationSeconds();
            ObjectNode clip = clips.addObject();
            clip.put("id", "source-video"); clip.put("trackId", "video-1");
            clip.put("sourceClipIndex", 1);
            clip.put("sourceStartSeconds", 0); clip.put("sourceEndSeconds", duration);
            clip.put("timelineStartSeconds", 0); clip.put("durationSeconds", duration);
            clip.put("sourceVolume", 1.0); clip.put("muted", false); clip.putObject("color")
                    .put("brightness", 0).put("contrast", 1).put("saturation", 1).put("temperature", 0);
            clip.putArray("keyframes"); cursor = duration;
        }
        timeline.put("durationSeconds", cursor); return timeline;
    }

    private void addTrack(ArrayNode tracks, String id, String type, String name, int order) {
        tracks.addObject().put("id", id).put("type", type).put("name", name).put("order", order)
                .put("muted", false).put("solo", false).put("locked", false);
    }

    private void split(ObjectNode timeline, String clipId, double at) {
        ArrayNode clips = (ArrayNode) timeline.path("clips"); ObjectNode clip = clip(clips, clipId);
        double start = clip.path("timelineStartSeconds").asDouble(), duration = clip.path("durationSeconds").asDouble();
        if (at <= start + .04 || at >= start + duration - .04) throw new IllegalArgumentException("分割点必须位于片段内部");
        double left = at - start; ObjectNode right = clip.deepCopy(); right.put("id", UUID.randomUUID().toString());
        right.put("timelineStartSeconds", at); right.put("sourceStartSeconds", clip.path("sourceStartSeconds").asDouble() + left);
        right.put("durationSeconds", duration - left); clip.put("sourceEndSeconds", right.path("sourceStartSeconds").asDouble());
        clip.put("durationSeconds", left); clips.add(right);
    }

    private void delete(ObjectNode timeline, String clipId) {
        ArrayNode clips = (ArrayNode) timeline.path("clips");
        if (clips.size() <= 1) throw new IllegalArgumentException("时间线至少需要保留一个片段");
        for (int index = 0; index < clips.size(); index++) {
            if (clipId.equals(clips.get(index).path("id").asText())) {
                clips.remove(index);
                return;
            }
        }
        throw new IllegalArgumentException("片段不存在");
    }

    private void mergeWithNext(ObjectNode timeline, String clipId) {
        ArrayNode clips = (ArrayNode) timeline.path("clips");
        List<ObjectNode> ordered = new ArrayList<>();
        clips.forEach(item -> ordered.add((ObjectNode) item));
        ordered.sort(Comparator.comparingDouble(item -> item.path("timelineStartSeconds").asDouble()));
        for (int index = 0; index < ordered.size(); index++) {
            ObjectNode left = ordered.get(index);
            if (!clipId.equals(left.path("id").asText())) continue;
            if (index + 1 >= ordered.size()) throw new IllegalArgumentException("所选片段右侧没有可连接片段");
            ObjectNode right = ordered.get(index + 1);
            if (!left.path("trackId").asText().equals(right.path("trackId").asText())) {
                throw new IllegalArgumentException("只能连接同一轨道上的片段");
            }
            if (Math.abs(left.path("sourceEndSeconds").asDouble()
                    - right.path("sourceStartSeconds").asDouble()) > .02) {
                throw new IllegalArgumentException("两个片段在源视频上不连续，不能直接连接");
            }
            ArrayNode sources = mapper.createArrayNode();
            appendSourceIndexes(sources, left);
            appendSourceIndexes(sources, right);
            left.set("sourceClipIndexes", sources);
            left.put("sourceEndSeconds", right.path("sourceEndSeconds").asDouble());
            left.put("durationSeconds", left.path("sourceEndSeconds").asDouble()
                    - left.path("sourceStartSeconds").asDouble());
            for (int raw = 0; raw < clips.size(); raw++) {
                if (right.path("id").asText().equals(clips.get(raw).path("id").asText())) {
                    clips.remove(raw);
                    return;
                }
            }
        }
        throw new IllegalArgumentException("片段不存在");
    }

    private void appendSourceIndexes(ArrayNode target, JsonNode clip) {
        if (clip.path("sourceClipIndexes").isArray()) clip.path("sourceClipIndexes").forEach(target::add);
        else target.add(clip.path("sourceClipIndex").asInt());
    }

    private void move(ObjectNode timeline, String clipId, String trackId, double start, boolean snap) {
        ObjectNode clip = clip((ArrayNode) timeline.path("clips"), clipId);
        if (timeline.path("tracks").findValuesAsText("id").stream().noneMatch(trackId::equals)) throw new IllegalArgumentException("轨道不存在");
        clip.put("trackId", trackId); clip.put("timelineStartSeconds", snap ? snapped(timeline, clipId, start) : Math.max(0, start));
        recalculateDuration(timeline);
    }

    private double snapped(ObjectNode timeline, String clipId, double value) {
        double best = Math.max(0, value), distance = timeline.path("snapSeconds").asDouble(.15);
        for (JsonNode item : timeline.path("clips")) if (!clipId.equals(item.path("id").asText())) {
            for (double edge : new double[]{item.path("timelineStartSeconds").asDouble(), item.path("timelineStartSeconds").asDouble() + item.path("durationSeconds").asDouble()})
                if (Math.abs(edge - value) <= distance) { best = edge; distance = Math.abs(edge - value); }
        }
        return best;
    }

    private void trim(ObjectNode timeline, String clipId, double sourceStart, double sourceEnd) {
        if (sourceEnd <= sourceStart + .04) throw new IllegalArgumentException("修剪后片段过短");
        ObjectNode clip = clip((ArrayNode) timeline.path("clips"), clipId);
        clip.put("sourceStartSeconds", sourceStart); clip.put("sourceEndSeconds", sourceEnd);
        clip.put("durationSeconds", sourceEnd - sourceStart); recalculateDuration(timeline);
    }

    private void trackState(ObjectNode timeline, String id, boolean muted, boolean solo) {
        for (JsonNode item : timeline.path("tracks")) if (id.equals(item.path("id").asText())) {
            ((ObjectNode) item).put("muted", muted).put("solo", solo); return;
        }
        throw new IllegalArgumentException("轨道不存在");
    }

    private void keyframe(ObjectNode timeline, String id, String property, double time, double value) {
        if (!Set.of("scale", "x", "y", "opacity", "volume").contains(property)) throw new IllegalArgumentException("不支持的关键帧属性");
        ObjectNode clip = clip((ArrayNode) timeline.path("clips"), id); ArrayNode frames = (ArrayNode) clip.withArray("keyframes");
        for (JsonNode frame : frames) if (property.equals(frame.path("property").asText()) && Math.abs(time-frame.path("timeSeconds").asDouble()) < .001) {
            ((ObjectNode) frame).put("value", value); return;
        }
        frames.addObject().put("property", property).put("timeSeconds", time).put("value", value);
    }

    private void color(ObjectNode timeline, String id, Map<String, Object> values) {
        ObjectNode color = clip((ArrayNode) timeline.path("clips"), id).with("color");
        color.put("brightness", bounded(number(values, "brightness"), -1, 1));
        color.put("contrast", bounded(number(values, "contrast"), 0, 3));
        color.put("saturation", bounded(number(values, "saturation"), 0, 3));
        color.put("temperature", bounded(number(values, "temperature"), -1, 1));
    }

    private JsonNode undo(UUID id) {
        UUID current = currentRevision(id); UUID parent = jdbc.queryForObject("SELECT parent_revision_id FROM project_revision WHERE id=?", UUID.class, current);
        if (parent != null) jdbc.update("UPDATE video_project SET current_revision_id=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", parent, id);
        ObjectNode result = timelineFrom(currentManifest(id), id);
        syncRenderableStoryboard(id, result);
        attachHistory(result, id);
        return result;
    }
    private JsonNode redo(UUID id) {
        UUID current = currentRevision(id);
        List<UUID> children = jdbc.query("SELECT id FROM project_revision WHERE project_id=? AND parent_revision_id=? ORDER BY revision_no DESC", (rs,n)->rs.getObject(1,UUID.class), id,current);
        if (!children.isEmpty()) jdbc.update("UPDATE video_project SET current_revision_id=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", children.getFirst(), id);
        ObjectNode result = timelineFrom(currentManifest(id), id);
        syncRenderableStoryboard(id, result);
        attachHistory(result, id);
        return result;
    }

    private void normalizeClipOrder(ObjectNode timeline) {
        List<ObjectNode> ordered = new ArrayList<>();
        timeline.withArray("clips").forEach(item -> ordered.add((ObjectNode) item));
        ordered.sort(Comparator.comparingDouble(item -> item.path("timelineStartSeconds").asDouble()));
        ArrayNode normalized = mapper.createArrayNode();
        double cursor = 0;
        for (ObjectNode item : ordered) {
            item.put("timelineStartSeconds", cursor);
            cursor += item.path("durationSeconds").asDouble();
            normalized.add(item);
        }
        timeline.set("clips", normalized);
        timeline.put("durationSeconds", cursor);
    }

    private void attachHistory(ObjectNode timeline, UUID id) {
        UUID current = currentRevision(id);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM project_revision WHERE project_id=?", Integer.class, id);
        UUID parent = jdbc.queryForObject("SELECT parent_revision_id FROM project_revision WHERE id=?", UUID.class, current);
        Integer children = jdbc.queryForObject("SELECT COUNT(*) FROM project_revision WHERE project_id=? AND parent_revision_id=?", Integer.class, id, current);
        timeline.putObject("history").put("revisionCount", count == null ? 0 : count)
                .put("canUndo", parent != null).put("canRedo", children != null && children > 0);
    }

    private void syncRenderableStoryboard(UUID id, ObjectNode timeline) {
        if (timeline.path("clips").isEmpty()) return;
        VideoTask task = requireTask(id);
        if (task.getGeneratedScriptPath() == null || task.getHighlightManifestPath() == null) return;
        remapAssetPlacements(id, timeline);
        workspace.applyEditorTimeline(id, timeline);
    }

    private void remapAssetPlacements(UUID taskId, ObjectNode timeline) {
        List<Map<String, Object>> existing = jdbc.queryForList("""
                SELECT clip_index,asset_id,placement_type,position_name,instruction,
                       ai_assigned,cutout_applied,created_at
                FROM storyboard_asset_placement WHERE task_id=?
                """, taskId);
        if (existing.isEmpty()) return;
        jdbc.update("DELETE FROM storyboard_asset_placement WHERE task_id=?", taskId);
        int targetIndex = 1;
        for (JsonNode clip : timeline.path("clips")) {
            Set<Integer> sourceIndexes = new LinkedHashSet<>();
            if (clip.path("sourceClipIndexes").isArray()) {
                clip.path("sourceClipIndexes").forEach(item -> sourceIndexes.add(item.asInt()));
            } else sourceIndexes.add(clip.path("sourceClipIndex").asInt(targetIndex));
            for (Map<String, Object> placement : existing) {
                if (!sourceIndexes.contains(((Number) placement.get("CLIP_INDEX")).intValue())) continue;
                jdbc.update("""
                        INSERT INTO storyboard_asset_placement(id,task_id,clip_index,asset_id,placement_type,
                            position_name,instruction,ai_assigned,cutout_applied,created_at)
                        VALUES(?,?,?,?,?,?,?,?,?,?)
                        """, UUID.randomUUID(), taskId, targetIndex, placement.get("ASSET_ID"),
                        placement.get("PLACEMENT_TYPE"), placement.get("POSITION_NAME"), placement.get("INSTRUCTION"),
                        placement.get("AI_ASSIGNED"), placement.get("CUTOUT_APPLIED"), placement.get("CREATED_AT"));
            }
            targetIndex++;
        }
    }

    private ObjectNode currentManifest(UUID id) { requireTask(id); try { return (ObjectNode) mapper.readTree(jdbc.queryForObject("SELECT manifest_json FROM project_revision WHERE id=?", String.class, currentRevision(id))); } catch(Exception e){throw new IllegalStateException("工程清单无法读取",e);} }
    private UUID currentRevision(UUID id) { return jdbc.queryForObject("SELECT current_revision_id FROM video_project WHERE id=?", UUID.class, id); }
    private void saveRevision(UUID id, ObjectNode manifest, String type, String summary) {
        try {
            UUID parent=currentRevision(id), revision=UUID.randomUUID(); String json=mapper.writeValueAsString(manifest);
            Integer no=jdbc.queryForObject("SELECT COALESCE(MAX(revision_no),0)+1 FROM project_revision WHERE project_id=?",Integer.class,id);
            jdbc.update("INSERT INTO project_revision(id,project_id,revision_no,parent_revision_id,created_by,change_type,change_summary,parameter_snapshot_json,manifest_json,manifest_schema_version,manifest_sha256,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                    revision,id,no,parent,currentUser.userId(),type,summary,"{}",json,3,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8))),OffsetDateTime.now());
            jdbc.update("UPDATE video_project SET current_revision_id=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",revision,id);
        } catch(Exception e){throw new IllegalStateException("无法保存剪辑版本",e);}
    }
    private ObjectNode clip(ArrayNode clips,String id){for(JsonNode n:clips)if(id.equals(n.path("id").asText()))return(ObjectNode)n;throw new IllegalArgumentException("片段不存在");}
    private void recalculateDuration(ObjectNode t){double end=0;for(JsonNode n:t.path("clips"))end=Math.max(end,n.path("timelineStartSeconds").asDouble()+n.path("durationSeconds").asDouble());t.put("durationSeconds",end);}
    private VideoTask requireTask(UUID id){return tasks.findById(id).orElseThrow(()->new TaskNotFoundException(id));}
    private String text(Map<String,Object>v,String k){String s=Objects.toString(v.get(k),"").trim();if(s.isEmpty())throw new IllegalArgumentException(k+"不能为空");return s;}
    private double number(Map<String,Object>v,String k){Object n=v.get(k);if(n instanceof Number x)return x.doubleValue();try{return Double.parseDouble(Objects.toString(n));}catch(Exception e){throw new IllegalArgumentException(k+"必须是数字");}}
    private boolean bool(Map<String,Object>v,String k,boolean d){Object n=v.get(k);return n==null?d:Boolean.parseBoolean(n.toString());}
    private double bounded(double v,double min,double max){return Math.max(min,Math.min(max,v));}
}
