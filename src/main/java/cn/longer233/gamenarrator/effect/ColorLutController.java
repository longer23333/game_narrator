package cn.longer233.gamenarrator.effect;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/tasks/{taskId}/color-lut")
public class ColorLutController {
    private final ColorLutService service;
    public ColorLutController(ColorLutService service) { this.service = service; }
    @GetMapping public ColorLutService.LutView status(@PathVariable UUID taskId) { return service.status(taskId); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ColorLutService.LutView upload(@PathVariable UUID taskId, @RequestParam("file") MultipartFile file) {
        return service.upload(taskId, file);
    }
    @DeleteMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID taskId) { service.delete(taskId); }
}
