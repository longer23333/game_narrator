package cn.longer233.gamenarrator.mobile;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class BilibiliQrLoginTest {
    @Test public void parsesSessionCookiesFromSetCookieHeaders() {
        List<String> headers = Arrays.asList(
                "SESSDATA=abc123; Path=/; HttpOnly",
                "bili_jct=xyz789; Path=/",
                "DedeUserID=42; Path=/; Expires=Sat, 01 Jan 2030 00:00:00 GMT");
        String cookies = BilibiliQrLogin.parseSetCookie(headers);
        assertEquals("SESSDATA=abc123; bili_jct=xyz789; DedeUserID=42", cookies);
    }

    @Test public void ignoresEmptyAndMalformedHeaders() {
        assertNull(BilibiliQrLogin.parseSetCookie(Arrays.asList("no-pair", "")));
        assertNull(BilibiliQrLogin.parseSetCookie(null));
    }

    @Test public void keepsQrcodeKeyParameterForAppConfirmationUrl() {
        String url = "https://account.bilibili.com/h5/account-h5/auth/scan-web"
                + "?navhide=1&callback=close&qrcode_key=abc123&from=";
        assertTrue(url.contains("qrcode_key=abc123"));
        assertTrue(url.startsWith("https://account.bilibili.com/h5/account-h5/auth/scan-web"));
    }

    @Test public void mapsReplayLoginStatesWithoutRealAccount() {
        assertEquals(BilibiliQrLogin.LoginState.CONFIRMED, BilibiliQrLogin.loginState(0));
        assertEquals(BilibiliQrLogin.LoginState.WAITING, BilibiliQrLogin.loginState(86090));
        assertEquals(BilibiliQrLogin.LoginState.WAITING, BilibiliQrLogin.loginState(86101));
        assertEquals(BilibiliQrLogin.LoginState.EXPIRED, BilibiliQrLogin.loginState(86038));
        assertEquals(BilibiliQrLogin.LoginState.FAILED, BilibiliQrLogin.loginState(-1));
    }
}
