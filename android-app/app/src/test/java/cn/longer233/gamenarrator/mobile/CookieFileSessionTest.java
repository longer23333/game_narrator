package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.Test;

public final class CookieFileSessionTest {
    @Test public void parsesNetscapeCookiesAndMatchesDomains() throws Exception {
        String fixture = "# Netscape HTTP Cookie File\n"
                + ".bilibili.com\tTRUE\t/\tFALSE\t0\tSESSDATA\tsecret123\n"
                + "#HttpOnly_bilibili.com\tTRUE\t/\tFALSE\t9999999999\tbili_jct\tjct456\n";
        Map<String, String> parsed = CookieFileSession.parse(
                new ByteArrayInputStream(fixture.getBytes(StandardCharsets.UTF_8)), 1_700_000_000L);
        assertEquals(2, parsed.size());
        assertTrue(parsed.containsKey(".bilibili.com"));
        assertEquals("SESSDATA=secret123", parsed.get(".bilibili.com"));

        CookieFileSession session = new CookieFileSession();
        session.load(new ByteArrayInputStream(fixture.getBytes(StandardCharsets.UTF_8)));
        assertTrue(session.isActive());
        assertEquals(2, session.domainCount());
        String header = session.cookiesForUrl("https://www.bilibili.com/video/1");
        assertTrue(header.contains("SESSDATA=secret123"));
        assertTrue(header.contains("bili_jct=jct456"));
    }

    @Test public void skipsExpiredAndMalformedLines() throws Exception {
        String fixture = ".bilibili.com\tTRUE\t/\tFALSE\t100\texpired\told\n"
                + "not-a-cookie-line\n"
                + ".example.com\tTRUE\t/\tFALSE\t0\tname\tvalue\n";
        Map<String, String> parsed = CookieFileSession.parse(
                new ByteArrayInputStream(fixture.getBytes(StandardCharsets.UTF_8)), 1_700_000_000L);
        assertEquals(1, parsed.size());
        assertFalse(parsed.containsKey(".bilibili.com"));
        assertEquals("name=value", parsed.get(".example.com"));
    }
}
