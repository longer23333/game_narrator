package cn.longer233.gamenarrator.effect;

import org.springframework.web.bind.annotation.GetMapping;
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
}
