package cn.longer233.gamenarrator.effect;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/effect-presets")
public class EffectController {
    private final EffectPresetCatalog catalog;

    public EffectController(EffectPresetCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<EffectPreset> list() {
        return catalog.all();
    }

    @PostMapping("/import")
    public EffectPreset importTemplate(@RequestBody EffectPreset template) {
        return catalog.importTemplate(template);
    }

    @GetMapping("/{code}/export")
    public org.springframework.http.ResponseEntity<EffectPreset> export(@PathVariable String code) {
        EffectPreset template = catalog.require(code);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=style-template-" + template.code().toLowerCase() + ".json")
                .body(template);
    }
}
