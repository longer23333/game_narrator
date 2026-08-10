package cn.longer233.gamenarrator.personalization;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/director-profile")
public class DirectorProfileController {
    private final DirectorProfileService service;
    public DirectorProfileController(DirectorProfileService service) { this.service = service; }
    @GetMapping public DirectorProfileView profile() { return service.profile(); }
}
