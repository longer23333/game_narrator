package cn.longer233.gamenarrator.storage;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/storage")
public class StorageAdminController {
    private final StorageAdminService service;
    public StorageAdminController(StorageAdminService service) { this.service = service; }
    @GetMapping public StorageAdminView inspect() { return service.inspect(); }
    @PostMapping("/cleanup") public StorageAdminView cleanup() { return service.cleanupAndInspect(); }
}
