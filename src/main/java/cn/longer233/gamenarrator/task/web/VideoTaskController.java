package cn.longer233.gamenarrator.task.web;

import cn.longer233.gamenarrator.task.application.*;
import cn.longer233.gamenarrator.task.domain.CommentaryStyle;
import cn.longer233.gamenarrator.task.domain.EditingScope;
import cn.longer233.gamenarrator.effect.EffectSettingsRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/tasks")
public class VideoTaskController {

    private final VideoTaskService service;
    private final TaskEventStreamService eventStream;
    private final cn.longer233.gamenarrator.render.RenderPreviewService renderPreviews;
    private final TaskTrashService trash;

    public VideoTaskController(VideoTaskService service, TaskEventStreamService eventStream,
            cn.longer233.gamenarrator.render.RenderPreviewService renderPreviews, TaskTrashService trash) {
        this.service = service;
        this.eventStream = eventStream;
        this.renderPreviews = renderPreviews;
        this.trash = trash;
    }

    @GetMapping
    public List<VideoTaskView> list() {
        return service.findAll();
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return eventStream.subscribe();
    }

    @GetMapping("/{id}")
    public VideoTaskView detail(@PathVariable UUID id) {
        return service.find(id);
    }

    @PatchMapping("/{id}/name")
    public VideoTaskView rename(@PathVariable UUID id,
                                @Valid @RequestBody RenameTaskRequest request) {
        return service.rename(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @GetMapping("/trash")
    public List<TaskTrashService.TrashedTask> trash() { return trash.list(); }

    @PostMapping("/trash/{id}/restore")
    public VideoTaskView restore(@PathVariable UUID id) { return trash.restore(id); }

    @DeleteMapping("/trash/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void purge(@PathVariable UUID id) { trash.purge(id); }

    @GetMapping("/{id}/output")
    public ResponseEntity<FileSystemResource> downloadOutput(@PathVariable UUID id) {
        var path = service.renderedVideo(id);
        String name = service.find(id).name().replaceAll("[\\r\\n\\\"\\\\/:*?<>|]", "_");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("video/mp4"))
                .contentLength(path.toFile().length())
                .header(HttpHeaders.CONTENT_DISPOSITION, org.springframework.http.ContentDisposition.attachment()
                        .filename(name + ".mp4", java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(path));
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<FileSystemResource> previewOutput(@PathVariable UUID id) {
        var path = service.renderedVideo(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("video/mp4"))
                .contentLength(path.toFile().length())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=preview-" + id + ".mp4")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(new FileSystemResource(path));
    }

    @GetMapping("/{id}/source")
    public ResponseEntity<FileSystemResource> previewSource(@PathVariable UUID id) {
        var path = service.sourceVideo(id);
        MediaType mediaType = org.springframework.http.MediaTypeFactory.getMediaType(path.getFileName().toString())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(path.toFile().length())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=source-" + id)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(new FileSystemResource(path));
    }

    @GetMapping("/{id}/render-preview")
    public List<cn.longer233.gamenarrator.render.RenderPreviewService.PreviewFrame> renderPreview(
            @PathVariable UUID id) {
        return renderPreviews.frames(id);
    }

    @GetMapping("/{id}/render-preview/{index}")
    public ResponseEntity<FileSystemResource> renderPreviewFrame(@PathVariable UUID id, @PathVariable int index) {
        Path path = renderPreviews.frame(id, index);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .contentLength(path.toFile().length())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(new FileSystemResource(path));
    }

    @PostMapping("/{id}/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void start(@PathVariable UUID id) {
        service.start(id);
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public VideoTaskView cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public VideoTaskView retry(@PathVariable UUID id) {
        return service.retry(id);
    }

    @PostMapping("/{id}/storyboard/approve")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public VideoTaskView approveStoryboard(@PathVariable UUID id) {
        return service.approveStoryboard(id);
    }

    @PostMapping("/{id}/rerender-effects")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void rerenderEffects(@PathVariable UUID id,
            @Valid @RequestBody(required = false) EffectSettingsRequest settings) {
        service.rerenderEffects(id, settings == null ? EffectSettingsRequest.defaults() : settings);
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public VideoTaskView create(
            @RequestParam("name") String name,
            @RequestParam("gameCategory") String gameCategory,
            @RequestParam("commentaryStyle") CommentaryStyle commentaryStyle,
            @RequestParam("targetDurationSeconds") int targetDurationSeconds,
            @RequestParam(value = "editingScope", defaultValue = "FULL_VIDEO") EditingScope editingScope,
            @RequestParam("taskBrief") String taskBrief,
            @RequestParam(value = "terminologyGlossary", defaultValue = "") String terminologyGlossary,
            @RequestParam(value = "storyboardReviewEnabled", defaultValue = "false") boolean storyboardReviewEnabled,
            @RequestParam(value = "automaticGenerationEnabled", defaultValue = "true") boolean automaticGenerationEnabled,
            @RequestParam(value = "cloudVisionEnabled", defaultValue = "false") boolean cloudVisionEnabled,
            @RequestParam(value = "aiScriptEnabled", defaultValue = "false") boolean aiScriptEnabled,
            @RequestParam(value = "aiVoiceEnabled", defaultValue = "false") boolean aiVoiceEnabled,
            @RequestParam(value = "autoAssetsEnabled", defaultValue = "false") boolean autoAssetsEnabled,
            @RequestParam("video") MultipartFile video
    ) throws IOException {
        CreateVideoTaskCommand command = new CreateVideoTaskCommand(
                name,
                gameCategory,
                commentaryStyle,
                targetDurationSeconds,
                editingScope,
                taskBrief,
                terminologyGlossary,
                storyboardReviewEnabled,
                automaticGenerationEnabled,
                cloudVisionEnabled,
                aiScriptEnabled,
                aiVoiceEnabled,
                autoAssetsEnabled
        );
        return service.create(command, video);
    }
}
