package cn.longer233.gamenarrator.mobile;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class RemoteMediaImporterTest {
    @Test public void rejectsNonHttpSchemeBeforeConnecting() {
        try {
            RemoteMediaImporter.resolveDirectUrl("javascript:alert(1)", "AUTO");
            fail("expected IllegalArgumentException");
        } catch (Exception error) {
            assertTrue(error instanceof IllegalArgumentException);
            assertTrue(error.getMessage().contains("HTTP"));
        }
    }

    @Test public void rejectsEmbeddedCredentialsBeforeConnecting() {
        try {
            RemoteMediaImporter.resolveDirectUrl("https://user:pass@example.com/video.mp4", "AUTO");
            fail("expected IllegalArgumentException");
        } catch (Exception error) {
            assertTrue(error instanceof IllegalArgumentException);
            assertTrue(error.getMessage().contains("用户名"));
        }
    }
}
