package cn.longer233.gamenarrator.mobile;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlatformLoginSessionTest {
    @Test public void allowsOnlyHttpsBilibiliHosts() {
        assertTrue(PlatformLoginSession.isTrustedLoginUrl("https://passport.bilibili.com/login"));
        assertTrue(PlatformLoginSession.isTrustedLoginUrl("https://www.bilibili.com/"));
        assertFalse(PlatformLoginSession.isTrustedLoginUrl("http://passport.bilibili.com/login"));
        assertFalse(PlatformLoginSession.isTrustedLoginUrl("https://bilibili.com.example.test/"));
        assertFalse(PlatformLoginSession.isTrustedLoginUrl("https://bilibili.com@evil.example/"));
        assertFalse(PlatformLoginSession.isTrustedLoginUrl("not a url"));
    }

    @Test public void detectsSessionCookieAcrossPassportAndMainDomains() {
        assertTrue(PlatformLoginSession.hasSessionCookie(
                "bili_jct=csrf", "DedeUserID=42; SESSDATA=session-value"));
        assertFalse(PlatformLoginSession.hasSessionCookie("bili_jct=csrf", null));
        assertFalse(PlatformLoginSession.hasSessionCookie((String[]) null));
    }

    @Test public void trustedLoginPolicyIncludesOnlyBilibiliHosts() {
        assertTrue(PlatformLoginSession.isTrustedLoginUrl("https://api.bilibili.com/x/web-interface/nav"));
        assertFalse(PlatformLoginSession.isTrustedLoginUrl("https://example.com/video.mp4"));
        assertFalse(PlatformLoginSession.isTrustedLoginUrl("https://bilibili.com.evil.example/video"));
    }

    @Test public void nativeSessionRejectsMissingOrBlankCookies() {
        assertTrue(PlatformLoginSession.normalizeNativeCookies("  SESSDATA=session-value  ")
                .equals("SESSDATA=session-value"));
        assertTrue(PlatformLoginSession.normalizeNativeCookies(null) == null);
        assertTrue(PlatformLoginSession.normalizeNativeCookies("   ") == null);
    }
}
