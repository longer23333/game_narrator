package cn.longer233.gamenarrator.script;

import cn.longer233.gamenarrator.common.AtomicArtifactWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

/** Owns filesystem validation and JSON serialization for the script workspace. */
final class ScriptArtifactStore {
    private final ObjectMapper objectMapper;

    ScriptArtifactStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    Path requireFile(String value, String missingPathMessage, String missingFileMessage) {
        if (value == null || value.isBlank()) throw new IllegalStateException(missingPathMessage);
        Path path = Path.of(value).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) throw new IllegalStateException(missingFileMessage);
        return path;
    }

    JsonNode readJson(Path path) {
        try {
            return objectMapper.readTree(path.toFile());
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read JSON artifact: " + exception.getMessage(), exception);
        }
    }

    void writeJson(Path target, Object value) {
        try {
            AtomicArtifactWriter.writeJson(objectMapper, target, value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot save script artifact: " + exception.getMessage(), exception);
        }
    }
}
