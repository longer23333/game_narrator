package cn.longer233.gamenarrator.cloud;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/cloud-sync")
public class CloudSyncController {
    private final CloudSyncService service;
    public CloudSyncController(CloudSyncService service) { this.service = service; }
    @GetMapping public List<Map<String,Object>> mine() { return service.mine(); }
    @PostMapping("/{id}/retry") public Map<String,Boolean> retry(@PathVariable UUID id) {
        service.retry(id);
        return Map.of("queued", true);
    }
}
