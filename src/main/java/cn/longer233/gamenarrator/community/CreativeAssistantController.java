package cn.longer233.gamenarrator.community;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

@RestController
@RequestMapping("/api/creative-assistant/sessions")
public class CreativeAssistantController {
    private final CreativeAssistantService service;
    public CreativeAssistantController(CreativeAssistantService service) { this.service = service; }
    @PostMapping public CreativeAssistantSessionView create(@RequestBody CreateRequest request) { return service.create(request.brief()); }
    @GetMapping("/{id}") public CreativeAssistantSessionView get(@PathVariable UUID id) { return service.get(id); }
    @GetMapping("/{id}/asset-requirements") public JsonNode assetRequirements(@PathVariable UUID id,
            @RequestParam String type) { return service.assetRequirements(id, type); }
    @PostMapping(value = "/{id}/materialize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CreativeAssistantSessionView materialize(@PathVariable UUID id, @RequestParam String type,
            @RequestParam(required = false) String name, @RequestPart("video") MultipartFile video) {
        return service.materialize(id, type, name, video);
    }
    public record CreateRequest(String brief) { }
}
