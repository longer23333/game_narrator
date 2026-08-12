package cn.longer233.gamenarrator.community;

import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks/{taskId}/variants")
public class CreativeVariantController {
    private final CreativeVariantService service;
    public CreativeVariantController(CreativeVariantService service) { this.service = service; }
    @GetMapping public List<CreativeVariantView> list(@PathVariable UUID taskId) { return service.list(taskId); }
    @PostMapping("/generate") public List<CreativeVariantView> generate(@PathVariable UUID taskId) { return service.generate(taskId); }
    @PostMapping("/{variantId}/materialize") public CreativeVariantView materialize(@PathVariable UUID taskId,
            @PathVariable UUID variantId) { return service.materialize(taskId, variantId); }
}
