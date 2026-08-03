package cn.longer233.gamenarrator.vision;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;

@RestController
@RequestMapping("/api/video-segments")
public class VideoSegmentSearchController {
    private final VideoSegmentSemanticIndex index;
    private final VideoSegmentClipService clipService;
    public VideoSegmentSearchController(VideoSegmentSemanticIndex index, VideoSegmentClipService clipService) {
        this.index = index;
        this.clipService = clipService;
    }

    @GetMapping("/search")
    public List<VideoSegmentSearchResult> search(@RequestParam String query,
                                                 @RequestParam(defaultValue = "12") int limit) {
        return index.search(query, limit);
    }

    @PostMapping(value = "/search-image", consumes = "multipart/form-data")
    public List<VideoSegmentSearchResult> searchImage(@RequestPart("image") MultipartFile image,
                                                       @RequestParam(defaultValue = "12") int limit) throws Exception {
        return index.searchByImage(image.getBytes(), limit);
    }

    @GetMapping("/{taskId}/{frameIndex}/thumbnail")
    public ResponseEntity<FileSystemResource> thumbnail(@PathVariable UUID taskId, @PathVariable int frameIndex) {
        var path = index.thumbnail(taskId, frameIndex);
        return ResponseEntity.ok().contentType(MediaTypeFactory.getMediaType(path.getFileName().toString())
                        .orElse(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM))
                .cacheControl(CacheControl.noCache()).body(new FileSystemResource(path));
    }

    @GetMapping("/{taskId}/clip")
    public ResponseEntity<FileSystemResource> clip(@PathVariable UUID taskId,
                                                    @RequestParam double startSeconds,
                                                    @RequestParam(defaultValue = "10") double durationSeconds,
                                                    @RequestParam(defaultValue = "false") boolean mute,
                                                    @RequestParam(defaultValue = "false") boolean download)
            throws Exception {
        var path = clipService.create(taskId, startSeconds, durationSeconds, mute);
        var resource = new FileSystemResource(path);
        String disposition = download ? "attachment" : "inline";
        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.valueOf("video/mp4"))
                .contentLength(resource.contentLength())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"segment.mp4\"")
                .body(resource);
    }

}
