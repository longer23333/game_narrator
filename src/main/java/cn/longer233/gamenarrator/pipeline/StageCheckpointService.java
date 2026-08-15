package cn.longer233.gamenarrator.pipeline;

import cn.longer233.gamenarrator.task.domain.ProcessingStageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Validates durable stage outputs before a completed stage is reused. */
@Service
public class StageCheckpointService {
    private static final Logger log = LoggerFactory.getLogger(StageCheckpointService.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public StageCheckpointService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public Validation reconcile(UUID taskId) {
        jdbc.queryForObject("SELECT id FROM video_tasks WHERE id=? FOR UPDATE", UUID.class, taskId);
        List<StageRow> completed = jdbc.query("""
                SELECT stage_type,sequence_number FROM processing_stages
                WHERE task_id=? AND status='COMPLETED' ORDER BY sequence_number
                """, (rs, row) -> new StageRow(ProcessingStageType.valueOf(rs.getString(1)), rs.getInt(2)), taskId);
        for (StageRow row : completed) {
            String problem = validate(taskId, row.type());
            if (problem != null) {
                invalidate(taskId, row, problem);
                return new Validation(false, row.type(), problem);
            }
        }
        return new Validation(true, null, null);
    }

    private String validate(UUID taskId, ProcessingStageType stage) {
        if (stage == ProcessingStageType.VIDEO_INGESTION) {
            String source = jdbc.queryForObject("SELECT source_video_path FROM video_tasks WHERE id=?", String.class, taskId);
            return validFile(source, false, null) ? null : "source media is missing or empty";
        }
        List<ArtifactRow> artifacts = jdbc.query("""
                SELECT artifact_type,storage_key,size_bytes,sha256,mime_type FROM artifact
                WHERE project_id=? AND artifact_type IN (%s) AND deleted_at IS NULL
                ORDER BY created_at DESC
                """.formatted(placeholders(types(stage).size())), (rs, row) -> new ArtifactRow(
                rs.getString(1), rs.getString(2), rs.getLong(3), rs.getString(4), rs.getString(5)),
                parameters(taskId, types(stage)));
        if (artifacts.isEmpty()) {
            if (stage == ProcessingStageType.TRANSCRIPTION) {
                String text = jdbc.queryForObject("SELECT transcript_text FROM video_tasks WHERE id=?", String.class, taskId);
                return text != null ? null : "transcription checkpoint has neither text nor artifacts";
            }
            return "checkpoint has no registered artifacts";
        }
        String required = requiredType(stage);
        if (required != null && artifacts.stream().noneMatch(item -> required.equals(item.type()))) {
            return "checkpoint is missing required artifact: " + required;
        }
        for (ArtifactRow artifact : artifacts) {
            boolean mustBeNonEmpty = artifact.mimeType().contains("json")
                    || artifact.mimeType().startsWith("video/") || artifact.mimeType().startsWith("audio/");
            if (!validFile(artifact.path(), artifact.mimeType().contains("json"), artifact, mustBeNonEmpty)) {
                return "artifact is missing, malformed, or checksum-mismatched: " + artifact.path();
            }
        }
        return null;
    }

    private boolean validFile(String value, boolean json, ArtifactRow expected) {
        return validFile(value, json, expected, true);
    }

    private boolean validFile(String value, boolean json, ArtifactRow expected, boolean mustBeNonEmpty) {
        if (value == null || value.isBlank()) return false;
        try {
            Path path = Path.of(value).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path) || (mustBeNonEmpty && Files.size(path) <= 0)) return false;
            if (expected != null && (Files.size(path) != expected.size() || !sha256(path).equals(expected.sha256()))) return false;
            if (json) mapper.readTree(path.toFile());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void invalidate(UUID taskId, StageRow failed, String reason) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
                UPDATE processing_stages SET status='PENDING',progress=0,error_message=NULL
                WHERE task_id=? AND sequence_number>=?
                """, taskId, failed.sequence());
        clearTaskOutputs(taskId, failed.type());
        List<String> downstreamTypes = new ArrayList<>();
        for (ProcessingStageType value : ProcessingStageType.values()) {
            if (value.ordinal() >= failed.type().ordinal()) downstreamTypes.addAll(types(value));
        }
        if (!downstreamTypes.isEmpty()) jdbc.update("""
                UPDATE artifact SET deleted_at=? WHERE project_id=? AND artifact_type IN (%s) AND deleted_at IS NULL
                """.formatted(placeholders(downstreamTypes.size())), parameters(now, taskId, downstreamTypes));
        jdbc.update("UPDATE video_tasks SET status='READY',failure_reason=NULL WHERE id=?", taskId);
        jdbc.update("UPDATE video_project SET status='PROCESSING',updated_at=? WHERE id=?", now, taskId);
        log.warn("STAGE_CHECKPOINT_INVALIDATED taskId={} restartStage={} reason={}", taskId, failed.type(), reason);
    }

    private void clearTaskOutputs(UUID taskId, ProcessingStageType stage) {
        String assignments = switch (stage) {
            case VIDEO_INGESTION -> "duration_seconds=NULL,video_width=NULL,video_height=NULL,frames_per_second=NULL,video_codec=NULL,audio_codec=NULL,detected_scene_count=NULL,transcript_text=NULL,visual_summary=NULL,analyzed_frame_count=NULL,highlight_summary=NULL,selected_highlight_count=NULL,generated_title=NULL,script_synopsis=NULL,generated_narration=NULL,generated_script_segment_count=NULL,generated_voice_segment_count=NULL,planned_output_duration_seconds=NULL,voice_overflow_count=NULL,rendered_file_size_bytes=NULL";
            case SCENE_DETECTION -> "detected_scene_count=NULL,transcript_text=NULL,visual_summary=NULL,analyzed_frame_count=NULL,highlight_summary=NULL,selected_highlight_count=NULL,generated_title=NULL,script_synopsis=NULL,generated_narration=NULL,generated_script_segment_count=NULL,generated_voice_segment_count=NULL,planned_output_duration_seconds=NULL,voice_overflow_count=NULL,rendered_file_size_bytes=NULL";
            case TRANSCRIPTION -> "transcript_text=NULL,visual_summary=NULL,analyzed_frame_count=NULL,highlight_summary=NULL,selected_highlight_count=NULL,generated_title=NULL,script_synopsis=NULL,generated_narration=NULL,generated_script_segment_count=NULL,generated_voice_segment_count=NULL,planned_output_duration_seconds=NULL,voice_overflow_count=NULL,rendered_file_size_bytes=NULL";
            case VIDEO_UNDERSTANDING -> "visual_summary=NULL,analyzed_frame_count=NULL,highlight_summary=NULL,selected_highlight_count=NULL,generated_title=NULL,script_synopsis=NULL,generated_narration=NULL,generated_script_segment_count=NULL,generated_voice_segment_count=NULL,planned_output_duration_seconds=NULL,voice_overflow_count=NULL,rendered_file_size_bytes=NULL";
            case HIGHLIGHT_SELECTION -> "highlight_summary=NULL,selected_highlight_count=NULL,generated_title=NULL,script_synopsis=NULL,generated_narration=NULL,generated_script_segment_count=NULL,generated_voice_segment_count=NULL,planned_output_duration_seconds=NULL,voice_overflow_count=NULL,rendered_file_size_bytes=NULL";
            case SCRIPT_GENERATION -> "generated_title=NULL,script_synopsis=NULL,generated_narration=NULL,generated_script_segment_count=NULL,generated_voice_segment_count=NULL,planned_output_duration_seconds=NULL,voice_overflow_count=NULL,rendered_file_size_bytes=NULL";
            case VOICE_GENERATION -> "generated_voice_segment_count=NULL,planned_output_duration_seconds=NULL,voice_overflow_count=NULL,rendered_file_size_bytes=NULL";
            case TIMELINE_PLANNING -> "planned_output_duration_seconds=NULL,voice_overflow_count=NULL,rendered_file_size_bytes=NULL";
            case RENDERING -> "rendered_file_size_bytes=NULL";
        };
        jdbc.update("UPDATE video_tasks SET " + assignments + " WHERE id=?", taskId);
    }

    private List<String> types(ProcessingStageType stage) {
        return switch (stage) {
            case VIDEO_INGESTION -> List.of();
            case SCENE_DETECTION -> List.of("SCENE_MANIFEST", "EXTRACTED_AUDIO");
            case TRANSCRIPTION -> List.of("TRANSCRIPT_TEXT", "TRANSCRIPT_SUBTITLE", "TRANSCRIPT_DETAIL");
            case VIDEO_UNDERSTANDING -> List.of("VISION_ANALYSIS");
            case HIGHLIGHT_SELECTION -> List.of("HIGHLIGHT_MANIFEST");
            case SCRIPT_GENERATION -> List.of("SCRIPT_MANIFEST");
            case VOICE_GENERATION -> List.of("VOICE_MANIFEST");
            case TIMELINE_PLANNING -> List.of("TIMELINE_MANIFEST");
            case RENDERING -> List.of("RENDERED_VIDEO", "GENERATED_SUBTITLE");
        };
    }

    private String requiredType(ProcessingStageType stage) {
        return switch (stage) {
            case VIDEO_INGESTION -> null;
            case SCENE_DETECTION -> "SCENE_MANIFEST";
            case TRANSCRIPTION -> null;
            case VIDEO_UNDERSTANDING -> "VISION_ANALYSIS";
            case HIGHLIGHT_SELECTION -> "HIGHLIGHT_MANIFEST";
            case SCRIPT_GENERATION -> "SCRIPT_MANIFEST";
            case VOICE_GENERATION -> "VOICE_MANIFEST";
            case TIMELINE_PLANNING -> "TIMELINE_MANIFEST";
            case RENDERING -> "RENDERED_VIDEO";
        };
    }

    private String placeholders(int count) { return String.join(",", java.util.Collections.nCopies(count, "?")); }
    private Object[] parameters(Object first, List<String> values) {
        List<Object> result = new ArrayList<>(); result.add(first); result.addAll(values); return result.toArray();
    }
    private Object[] parameters(Object first, Object second, List<String> values) {
        List<Object> result = new ArrayList<>(); result.add(first); result.add(second); result.addAll(values); return result.toArray();
    }
    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) { input.transferTo(new java.security.DigestOutputStream(java.io.OutputStream.nullOutputStream(), digest)); }
        return HexFormat.of().formatHex(digest.digest());
    }

    public record Validation(boolean valid, ProcessingStageType restartStage, String reason) { }
    private record StageRow(ProcessingStageType type, int sequence) { }
    private record ArtifactRow(String type, String path, long size, String sha256, String mimeType) { }
}
