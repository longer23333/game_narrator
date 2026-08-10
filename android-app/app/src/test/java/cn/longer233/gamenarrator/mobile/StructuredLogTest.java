package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;
import org.junit.Test;

public class StructuredLogTest {
    @Test public void appendsJsonLinesAndReadsBack() throws Exception {
        File file = File.createTempFile("structured-log", ".jsonl");
        try {
            StructuredLog.append(file, 123L, "INFO", "app", "hello \"world\"");
            StructuredLog.append(file, 124L, "WARN", "export", "failed");
            String raw = StructuredLog.readAll(file);
            assertTrue(raw.contains("\"ts\":123"));
            assertTrue(raw.contains("\"level\":\"INFO\""));
            assertTrue(raw.contains("\\\"world\\\""));
            assertEquals(2, StructuredLog.count(file));
            List<String> tail = StructuredLog.tail(file, 1);
            assertEquals(1, tail.size());
            assertTrue(tail.get(0).contains("\"source\":\"export\""));
        } finally {
            file.delete();
        }
    }

    @Test public void compactKeepsLatestEntriesOnly() throws Exception {
        File file = File.createTempFile("structured-log-compact", ".jsonl");
        try {
            for (int i = 0; i < StructuredLog.MAX_ENTRIES + 250; i++) {
                StructuredLog.append(file, i, "INFO", "test", "entry " + i);
            }
            assertEquals(StructuredLog.MAX_ENTRIES, StructuredLog.count(file));
            String raw = StructuredLog.readAll(file);
            assertTrue(raw.contains("entry " + (StructuredLog.MAX_ENTRIES + 249)));
            assertFalse(raw.contains("entry 0"));
        } finally {
            file.delete();
        }
    }

    @Test public void clearRemovesFile() throws Exception {
        File file = File.createTempFile("structured-log-clear", ".jsonl");
        StructuredLog.append(file, 1L, "INFO", "test", "one");
        assertTrue(file.isFile());
        assertTrue(StructuredLog.clear(file));
        assertFalse(file.exists());
    }
}
