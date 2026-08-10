package cn.longer233.gamenarrator.admin;

import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/management")
public class AdminManagementController {
    private final AdminManagementService service;
    public AdminManagementController(AdminManagementService service) { this.service=service; }
    @GetMapping("/summary") public Map<String,Object> summary(){return service.summary();}
    @GetMapping("/users") public List<Map<String,Object>> users(){return service.users();}
    @PatchMapping("/users/{id}") public Map<String,Boolean> updateUser(@PathVariable UUID id,@RequestBody AdminManagementService.UserUpdate body){service.updateUser(id,body);return Map.of("updated",true);}
    @PostMapping("/users/{id}/revoke-sessions") public Map<String,Boolean> revoke(@PathVariable UUID id){service.revokeSessions(id);return Map.of("revoked",true);}
    @GetMapping("/projects") public List<Map<String,Object>> projects(){return service.projects();}
    @GetMapping("/api-usage") public List<Map<String,Object>> usage(){return service.usage();}
    @GetMapping("/cloud-sync") public List<Map<String,Object>> sync(){return service.syncItems();}
    @GetMapping("/audit") public List<Map<String,Object>> audit(){return service.audit();}
}
