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
}
