package cn.longer233.gamenarrator.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AtomicArtifactWriterTest {
    @TempDir Path tempDirectory;

    @Test
    void replacesJsonAndLeavesNoTemporaryArtifact() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path target = tempDirectory.resolve("artifact.json");
        AtomicArtifactWriter.writeJson(mapper, target, Map.of("version", 1));
        AtomicArtifactWriter.writeJson(mapper, target, Map.of("version", 2));
        assertEquals(2, mapper.readTree(target.toFile()).path("version").asInt());
        try (var files = Files.list(tempDirectory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }
}
