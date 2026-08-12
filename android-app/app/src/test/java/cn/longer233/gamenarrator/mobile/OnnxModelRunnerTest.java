package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

    @Test public void distinguishesBundledUserInstalledAndMissingModels() throws Exception {
        File dir = Files.createTempDirectory("model-sources").toFile();
        assertTrue(new File(dir, "vision-bundled.onnx").createNewFile());
        assertTrue(new File(dir, "text-custom.onnx").createNewFile());

        assertEquals(MobileModelDirectory.Source.BUNDLED,
                MobileModelDirectory.source(dir, "vision", new String[]{"vision-bundled.onnx"}));
        assertEquals(MobileModelDirectory.Source.USER_INSTALLED,
                MobileModelDirectory.source(dir, "text", new String[]{"text-gpt2.onnx"}));
        assertEquals(MobileModelDirectory.Source.MISSING,
                MobileModelDirectory.source(dir, "embedding", new String[]{"text-embedding.onnx"}));
    }
}
