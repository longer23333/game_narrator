package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.nio.file.Files;
import org.junit.Test;

public class OnnxModelRunnerTest {
    @Test public void selectsModelsByKindDeterministically() throws Exception {
        File dir = Files.createTempDirectory("onnx-models").toFile();
        new File(dir, "text-z.onnx").createNewFile();
        new File(dir, "text-a.onnx").createNewFile();
        new File(dir, "text-embedding.onnx").createNewFile();
        new File(dir, "vision-mobile.onnx").createNewFile();

        assertEquals("text-a.onnx", OnnxModelRunner.modelFile(dir, "text").getName());
        assertEquals("text-embedding.onnx", OnnxModelRunner.modelFile(dir, "embedding").getName());
        assertEquals("vision-mobile.onnx", OnnxModelRunner.modelFile(dir, "vision").getName());
        assertNull(OnnxModelRunner.modelFile(dir, "unknown"));
    }
}
