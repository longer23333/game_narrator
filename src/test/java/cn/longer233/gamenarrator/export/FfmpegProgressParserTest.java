package cn.longer233.gamenarrator.export;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FfmpegProgressParserTest {
    @Test
    void mapsMicrosecondProgressIntoExportRange() {
        FfmpegProgressParser parser = new FfmpegProgressParser(100);

        assertEquals(10, parser.parsePercent("out_time_us=0").orElseThrow());
        assertEquals(50, parser.parsePercent("out_time_ms=50000000").orElseThrow());
        assertEquals(89, parser.parsePercent("out_time_us=120000000").orElseThrow());
    }

    @Test
    void parsesClockAndIgnoresUnrelatedOrMalformedLines() {
        FfmpegProgressParser parser = new FfmpegProgressParser(120);

        assertEquals(50, parser.parsePercent("out_time=00:01:00.000000").orElseThrow());
        assertTrue(parser.parsePercent("speed=1.2x").isEmpty());
        assertTrue(parser.parsePercent("out_time_us=N/A").isEmpty());
    }
}
