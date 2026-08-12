package cn.longer233.gamenarrator.script;

import cn.longer233.gamenarrator.voice.VoiceSegment;
import cn.longer233.gamenarrator.voice.VoiceOption;
import cn.longer233.gamenarrator.voice.VoiceRegenerationRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/tasks/{taskId}")
public class ScriptWorkspaceController {
    private final ScriptWorkspaceService service;
    private final StoryboardAssetPlacementService placements;

    public ScriptWorkspaceController(ScriptWorkspaceService service, StoryboardAssetPlacementService placements) {
        this.service = service;
        this.placements = placements;
    }

    @GetMapping("/script")
    public ScriptDocumentView script(@PathVariable UUID taskId) {
        return service.find(taskId);
    }

    @GetMapping("/script/reviews")
    public java.util.Map<String, Object> reviews(@PathVariable("taskId") UUID taskId) {
        return service.reviews(taskId);
    }

    @PostMapping("/script/quality-review")
    public ScriptDocumentView qualityReview(@PathVariable("taskId") UUID taskId) {
        return service.qualityReview(taskId);
    }

    @PutMapping("/script/segments/{clipIndex}/review")
    public java.util.Map<String, Object> review(@PathVariable("taskId") UUID taskId,
            @PathVariable("clipIndex") int clipIndex,
            @Valid @RequestBody ManualScriptReviewRequest request) {
        return service.review(taskId, clipIndex, request);
    }

    @PutMapping("/script/segments/{clipIndex}")
    public ScriptDocumentView update(@PathVariable UUID taskId, @PathVariable int clipIndex,
                                     @Valid @RequestBody UpdateScriptSegmentRequest request) {
        return service.update(taskId, clipIndex, request);
    }

    @PostMapping("/script/segments/{clipIndex}/regenerate")
    public ScriptDocumentView regenerate(@PathVariable UUID taskId, @PathVariable int clipIndex,
                                         @Valid @RequestBody(required = false)
                                         RegenerateScriptSegmentRequest request) {
        return service.regenerate(taskId, clipIndex, request);
    }

    @PostMapping("/voice/segments/{clipIndex}/regenerate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public VoiceSegment regenerateVoice(@PathVariable UUID taskId, @PathVariable int clipIndex,
                                        @Valid @RequestBody(required = false) VoiceRegenerationRequest request) {
        return service.regenerateVoice(taskId, clipIndex, request);
    }

    @GetMapping("/voice/options")
    public java.util.List<VoiceOption> voiceOptions() {
        return service.voiceOptions();
    }

    @GetMapping("/voice/profiles")
    public java.util.List<cn.longer233.gamenarrator.voice.VoiceProfile> voiceProfiles() {
        return service.voiceProfiles();
    }

    @PostMapping(value = "/voice/preview", produces = "audio/wav")
    public ResponseEntity<FileSystemResource> voicePreview(@Valid @RequestBody(required = false)
            cn.longer233.gamenarrator.voice.VoicePreviewRequest request) {
        var path = service.voicePreview(request == null ? null : request.settings(),
                request == null ? null : request.text());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/wav"))
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(new FileSystemResource(path));
    }

    @GetMapping("/storyboard")
    public StoryboardView storyboard(@PathVariable UUID taskId) {
        return service.storyboard(taskId);
    }

    @PutMapping("/storyboard/segments/{clipIndex}")
    public StoryboardView updateStoryboard(@PathVariable UUID taskId, @PathVariable int clipIndex,
                                           @Valid @RequestBody UpdateStoryboardSegmentRequest request) {
        return service.updateStoryboard(taskId, clipIndex, request);
    }

    @PostMapping("/storyboard/segments/{clipIndex}/move")
    public StoryboardView moveStoryboard(@PathVariable UUID taskId, @PathVariable int clipIndex,
                                         @Valid @RequestBody MoveStoryboardSegmentRequest request) {
        return service.moveStoryboard(taskId, clipIndex, request);
    }

    @GetMapping("/storyboard/segments/{clipIndex}/thumbnail")
    public ResponseEntity<FileSystemResource> storyboardThumbnail(@PathVariable UUID taskId,
                                                                   @PathVariable int clipIndex) {
        var path = service.storyboardThumbnail(taskId, clipIndex);
        MediaType mediaType = org.springframework.http.MediaTypeFactory.getMediaType(path.getFileName().toString())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(mediaType)
                .cacheControl(org.springframework.http.CacheControl.noCache())
                .body(new FileSystemResource(path));
    }

    @GetMapping("/storyboard/assets")
    public java.util.List<StoryboardAssetPlacementView> storyboardAssets(@PathVariable UUID taskId) {
        return placements.list(taskId);
    }

    @PostMapping("/storyboard/assets/auto")
    public AutoAssetAssignmentView autoAssets(@PathVariable UUID taskId) {
        return placements.autoAssign(taskId);
    }

    @PostMapping("/storyboard/segments/{clipIndex}/assets")
    @ResponseStatus(HttpStatus.CREATED)
    public StoryboardAssetPlacementView placeAsset(@PathVariable UUID taskId, @PathVariable int clipIndex,
                                                    @Valid @RequestBody PlaceStoryboardAssetRequest request) {
        return placements.place(taskId, clipIndex, request);
    }

    @DeleteMapping("/storyboard/assets/{placementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAsset(@PathVariable UUID taskId, @PathVariable UUID placementId) {
        placements.delete(taskId, placementId);
    }

    @PutMapping("/storyboard/assets/{placementId}")
    public StoryboardAssetPlacementView updateAsset(@PathVariable UUID taskId, @PathVariable UUID placementId,
                                                     @Valid @RequestBody UpdateStoryboardAssetRequest request) {
        return placements.update(taskId, placementId, request);
    }
}
