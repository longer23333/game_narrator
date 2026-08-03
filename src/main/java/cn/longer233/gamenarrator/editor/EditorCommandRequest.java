package cn.longer233.gamenarrator.editor;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record EditorCommandRequest(@NotBlank String type, Map<String, Object> payload) {
    public Map<String, Object> values() { return payload == null ? Map.of() : payload; }
}
