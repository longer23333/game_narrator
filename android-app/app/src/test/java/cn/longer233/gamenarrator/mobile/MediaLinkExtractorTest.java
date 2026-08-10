package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class MediaLinkExtractorTest {
    @Test public void extractsOgVideoAndResolvesRelativeUrls() {
        String html = "<html><head><meta property=\"og:video\" content=\"/media/clip.mp4\"></head>"
                + "<body><video src=\"https://cdn.example.com/a.webm\"></video>"
                + "<source src=\"//cdn.example.com/b.mov\" type=\"video/quicktime\"></body></html>";
        List<MediaLinkExtractor.Candidate> candidates = MediaLinkExtractor.extract(html, "https://example.com/page");
        assertEquals(3, candidates.size());
        assertEquals("https://example.com/media/clip.mp4", candidates.get(0).url());
        assertEquals("video/mp4", candidates.get(0).mimeType());
        assertEquals("https://cdn.example.com/a.webm", candidates.get(1).url());
        assertEquals("https://cdn.example.com/b.mov", candidates.get(2).url());
        assertEquals("video/quicktime", candidates.get(2).mimeType());
    }

    @Test public void emptyHtmlYieldsNoCandidates() {
        assertTrue(MediaLinkExtractor.extract("", "https://example.com").isEmpty());
        assertTrue(MediaLinkExtractor.extract(null, "https://example.com").isEmpty());
    }
}
