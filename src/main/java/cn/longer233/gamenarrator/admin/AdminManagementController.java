package cn.longer233.gamenarrator.admin;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/management")
public class AdminManagementController {
    private final AdminManagementService service;
    public AdminManagementController(AdminManagementService service) { this.service = service; }

    @GetMapping("/overview") public Map<String,Object> overview() { return service.overview(); }
    @GetMapping("/users") public AdminPage users(@RequestParam(defaultValue="") String search,
            @RequestParam(defaultValue="") String status, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="25") int size) { return service.users(search,status,page,size); }
    @GetMapping("/users/{id}") public Map<String,Object> user(@PathVariable UUID id) { return service.user(id); }
    @PatchMapping("/users/{id}") public Map<String,Boolean> updateUser(@PathVariable UUID id,
            @Valid @RequestBody AdminManagementService.UserUpdate body) { service.updateUser(id,body); return Map.of("updated",true); }
    @PostMapping("/users/{id}/revoke-sessions") public Map<String,Boolean> revoke(@PathVariable UUID id) { service.revokeSessions(id); return Map.of("revoked",true); }

    @GetMapping("/projects") public AdminPage projects(@RequestParam(defaultValue="") String search,
            @RequestParam(defaultValue="") String status, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="25") int size) { return service.projects(search,status,page,size); }
    @GetMapping("/projects/{id}") public Map<String,Object> project(@PathVariable UUID id) { return service.project(id); }
    @PostMapping("/projects/{id}/archive") public Map<String,Boolean> archive(@PathVariable UUID id,
            @RequestBody ArchiveRequest request) { service.setProjectArchived(id,request.archived()); return Map.of("updated",true); }

    @GetMapping("/api-usage") public Map<String,Object> usage(@RequestParam(required=false) LocalDate from,
            @RequestParam(required=false) LocalDate to, @RequestParam(defaultValue="") String search,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="25") int size) {
        return service.usage(from,to,search,page,size);
    }
    @GetMapping("/cloud-sync") public AdminPage sync(@RequestParam(defaultValue="") String search,
            @RequestParam(defaultValue="") String status, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="25") int size) { return service.syncItems(search,status,page,size); }
    @PostMapping("/cloud-sync/{id}/retry") public Map<String,Boolean> retrySync(@PathVariable UUID id) { service.retrySync(id); return Map.of("queued",true); }
    @GetMapping("/audit") public AdminPage audit(@RequestParam(defaultValue="") String search,
            @RequestParam(defaultValue="") String action, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="25") int size) { return service.audit(search,action,page,size); }

    public record ArchiveRequest(boolean archived) {}
}
