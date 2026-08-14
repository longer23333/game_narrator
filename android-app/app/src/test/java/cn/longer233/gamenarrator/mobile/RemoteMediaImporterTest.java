package cn.longer233.gamenarrator.mobile;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertEquals;
import java.net.URI;

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

    @Test public void validatesResumeRangeAndStableKey() throws Exception {
        assertTrue(RemoteMediaImporter.contentRangeStartsAt("bytes 1024-2047/4096",1024));
        assertTrue(!RemoteMediaImporter.contentRangeStartsAt("bytes 0-2047/4096",1024));
        assertEquals(RemoteMediaImporter.resumeKey(URI.create("https://example.com/a.mp4")),
                RemoteMediaImporter.resumeKey(URI.create("https://example.com/a.mp4")));
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

    @Test public void classifiesProtocolReplayAndFaultResponses() {
        assertEquals(RemoteMediaImporter.ResponseAction.ACCEPT_NEW, RemoteMediaImporter.responseAction(200,0,null));
        assertEquals(RemoteMediaImporter.ResponseAction.APPEND, RemoteMediaImporter.responseAction(206,1024,"bytes 1024-2047/4096"));
        assertEquals(RemoteMediaImporter.ResponseAction.RESTART, RemoteMediaImporter.responseAction(206,1024,"bytes 0-2047/4096"));
        assertEquals(RemoteMediaImporter.ResponseAction.RESTART, RemoteMediaImporter.responseAction(416,1024,null));
        assertEquals(RemoteMediaImporter.ResponseAction.RETRY_LATER, RemoteMediaImporter.responseAction(429,0,null));
        assertEquals(RemoteMediaImporter.ResponseAction.REAUTHENTICATE, RemoteMediaImporter.responseAction(403,0,null));
        assertEquals(RemoteMediaImporter.ResponseAction.RETRY_LATER, RemoteMediaImporter.responseAction(500,0,null));
        assertEquals(RemoteMediaImporter.ResponseAction.FAIL, RemoteMediaImporter.responseAction(404,0,null));
    }
}
