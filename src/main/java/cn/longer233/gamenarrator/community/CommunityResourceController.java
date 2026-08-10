package cn.longer233.gamenarrator.community;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/community")
public class CommunityResourceController {
    private final CommunityResourceService service;
    public CommunityResourceController(CommunityResourceService service) { this.service = service; }
    @GetMapping("/resources") public List<CommunityResourceView> list(@RequestParam(required = false) String type) { return service.list(type); }
    @PostMapping("/knowledge-packs/{code}") public CommunityResourceView publishPack(@PathVariable String code, @RequestBody(required = false) PublishCommunityResourceRequest request) { return service.publishKnowledgePack(code, request); }
    @PostMapping("/styles/{code}") public CommunityResourceView publishStyle(@PathVariable String code, @RequestBody(required = false) PublishCommunityResourceRequest request) { return service.publishStyle(code, request); }
    @PostMapping("/resources/{id}/install") public JsonNode install(@PathVariable UUID id) { return service.install(id); }
    @GetMapping(value="/resources/{id}/export", produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> export(@PathVariable UUID id) throws Exception {
        byte[] bytes = service.payload(id).toPrettyString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename("community-resource-" + id + ".json").build().toString())
                .contentType(MediaType.APPLICATION_JSON).body(bytes);
    }
}
