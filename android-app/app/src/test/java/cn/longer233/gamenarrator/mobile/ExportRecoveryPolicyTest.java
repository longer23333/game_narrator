package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import org.junit.Test;

public final class ExportRecoveryPolicyTest {
    @Test public void onlyProcessingIsRecoveredAsFailed() {
        assertTrue(ExportRecoveryPolicy.shouldMarkFailed("PROCESSING"));
        assertFalse(ExportRecoveryPolicy.shouldMarkFailed("COMPLETED"));
        assertFalse(ExportRecoveryPolicy.shouldMarkFailed("CANCELLED"));
        assertFalse(ExportRecoveryPolicy.shouldMarkFailed("FAILED"));
        assertFalse(ExportRecoveryPolicy.shouldMarkFailed(""));
    }

    @Test public void interruptedErrorIsStable() {
        assertEquals("Export interrupted because the app stopped", ExportRecoveryPolicy.INTERRUPTED_ERROR);
    }

    @Test public void cleansOnlyInterruptedAppOwnedOutputs() throws Exception {
        File temp = File.createTempFile("recovery-policy", ".dir");
        assertTrue(temp.delete());
        assertTrue(temp.mkdirs());
        File movies = new File(temp, "movies");
        assertTrue(movies.mkdirs());
        try {
            File partial = new File(movies, "partial.mp4");
            assertTrue(partial.createNewFile());
            File outside = new File(temp, "outside.mp4");
            assertTrue(outside.createNewFile());
            assertTrue(ExportRecoveryPolicy.shouldCleanOutput("FAILED",
                    ExportRecoveryPolicy.INTERRUPTED_ERROR, partial, movies));
            assertFalse(ExportRecoveryPolicy.shouldCleanOutput("FAILED", "disk full", partial, movies));
            assertFalse(ExportRecoveryPolicy.shouldCleanOutput("COMPLETED",
                    ExportRecoveryPolicy.INTERRUPTED_ERROR, partial, movies));
            assertFalse(ExportRecoveryPolicy.shouldCleanOutput("FAILED",
                    ExportRecoveryPolicy.INTERRUPTED_ERROR, outside, movies));
            assertFalse(ExportRecoveryPolicy.shouldCleanOutput("FAILED",
                    ExportRecoveryPolicy.INTERRUPTED_ERROR, null, movies));
        } finally {
            File[] children = temp.listFiles();
            if (children != null) {
                for (File child : children) child.delete();
            }
            temp.delete();
        }
    }
}
