package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import org.junit.Test;

public final class WhisperModelRunnerTest {
    @Test public void readinessRequiresModelAndEngine() throws Exception {
        File dir = Files.createTempDirectory("whisper-runner").toFile();
        try {
            assertFalse(WhisperModelRunner.isReady(dir));
            File model = new File(dir, "whisper-tiny.bin");
            assertTrue(model.createNewFile());
            assertFalse(WhisperModelRunner.isReady(dir));
            File engine = new File(dir, "whisper-cli");
            assertTrue(engine.createNewFile());
            assertTrue(WhisperModelRunner.isReady(dir));
            assertNotNull(WhisperModelRunner.engineFile(dir));
            assertNotNull(WhisperModelRunner.modelFile(dir));
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test public void windowsEngineNameIsAccepted() throws Exception {
        File dir = Files.createTempDirectory("whisper-runner-win").toFile();
        try {
            File model = new File(dir, "whisper-base.bin");
            assertTrue(model.createNewFile());
            File engine = new File(dir, "whisper-cli.exe");
            assertTrue(engine.createNewFile());
            assertTrue(WhisperModelRunner.isReady(dir));
        } finally {
            deleteRecursively(dir);
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }
}
