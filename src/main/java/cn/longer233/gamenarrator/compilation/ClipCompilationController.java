package cn.longer233.gamenarrator.compilation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/compilations")
public class ClipCompilationController {
    private final ClipCompilationService service;
    public ClipCompilationController(ClipCompilationService service) { this.service = service; }

    @GetMapping public List<ClipCompilationService.CompilationView> list() { return service.list(); }
    @GetMapping("/{id}") public ClipCompilationService.CompilationView find(@PathVariable UUID id) { return service.find(id); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ClipCompilationService.CompilationView create(@Valid @RequestBody CreateRequest request) {
        return service.create(request.name());
    }

    @PostMapping("/{id}/items")
    public ClipCompilationService.CompilationView add(@PathVariable UUID id, @Valid @RequestBody AddRequest request) {
        return service.add(id, request.taskId(), request.clipIndex());
    }

    @PutMapping("/{id}/order")
    public ClipCompilationService.CompilationView reorder(@PathVariable UUID id,
            @Valid @RequestBody ReorderRequest request) { return service.reorder(id, request.itemIds()); }

    public record CreateRequest(@NotBlank @Size(max=120) String name) {}
    public record AddRequest(@NotNull UUID taskId, @Positive int clipIndex) {}
    public record ReorderRequest(@NotEmpty List<@NotNull UUID> itemIds) {}
}
