package cn.longer233.gamenarrator.importer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import cn.longer233.gamenarrator.asset.AssetCatalogService;
import static org.mockito.Mockito.mock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YtDlpMediaImporterTest {

    @Test
    void buildsPlayableSelectorsForSeparatedVideoAndAudioStreams() {
        assertEquals("bestvideo+bestaudio/best", YtDlpMediaImporter.playableFormatSelector("best"));
        assertEquals("30064+bestaudio/30064/bestvideo+bestaudio/best",
                YtDlpMediaImporter.playableFormatSelector("30064"));
        assertEquals("30064+bestaudio/30064",
                YtDlpMediaImporter.playableFormatSelector("30064+bestaudio/30064"));
    }

    private final YtDlpMediaImporter importer = new YtDlpMediaImporter(
            new ObjectMapper(), new MediaImportProperties(), mock(AssetCatalogService.class),
            "./target/importer-test");

    @Test
    void recognizesCurlTlsResetAsImpersonationTransportFailure() {
        assertTrue(importer.isImpersonationFallbackFailure(new IllegalStateException(
                "curl: (35) Recv failure: Connection was reset (caused by SSLError)")));
    }

    @Test
    void doesNotTreatPlatformAuthenticationFailureAsTransportFailure() {
        assertFalse(importer.isImpersonationFallbackFailure(new IllegalStateException(
                "Bilibili rejected request with HTTP Error 412")));
    }

    @Test
    void retriesWithoutImpersonationWhenBilibiliTemporarilyReturnsNoFormats() {
        assertTrue(importer.isImpersonationFallbackFailure(new IllegalStateException(
                "ERROR: [BiliBili] BV1pW3q6rEZL: No video formats found!")));
    }
}
