package cn.longer233.gamenarrator.cloud;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cloud-sync")
public class CloudSyncController {
    private final CloudSyncService service;
    public CloudSyncController(CloudSyncService service) { this.service = service; }
    @GetMapping public List<Map<String,Object>> mine() { return service.mine(); }
}
