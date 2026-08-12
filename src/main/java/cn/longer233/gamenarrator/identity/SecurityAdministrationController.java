package cn.longer233.gamenarrator.identity;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/security")
public class SecurityAdministrationController {
    private final SecretRotationService secrets;

    public SecurityAdministrationController(SecretRotationService secrets) { this.secrets = secrets; }

    @PostMapping("/rotate-secrets")
    public SecretRotationService.RotationResult rotateSecrets() { return secrets.rotateAll(); }
}
