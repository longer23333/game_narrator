package cn.longer233.gamenarrator.identity;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.UUID;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private final AuthSessionService sessions = mock(AuthSessionService.class);
    private final CurrentUserContext current = mock(CurrentUserContext.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final AuthController controller = new AuthController(sessions, current, false);
    private final AuthSessionService.Account account = new AuthSessionService.Account(
            UUID.randomUUID(), "creator", "创作者", "USER", "ACTIVE", "REGISTERED");

    @Test
    void rememberedLoginCreatesThirtyDayPersistentCookie() {
        when(request.getHeader("User-Agent")).thenReturn("Browser");
        when(sessions.login("creator", "password", "Browser", true))
                .thenReturn(new AuthSessionService.LoginResult("secret-token", account));

        String cookie = controller.login(new AuthController.LoginRequest("creator", "password", true), request)
                .getHeaders().getFirst(HttpHeaders.SET_COOKIE);

        assertThat(cookie).contains("GN_SESSION=secret-token", "Max-Age=2592000", "HttpOnly", "SameSite=Lax");
    }

    @Test
    void regularLoginCreatesBrowserSessionCookieWithoutMaxAge() {
        when(request.getHeader("User-Agent")).thenReturn("Browser");
        when(sessions.login("creator", "password", "Browser", false))
                .thenReturn(new AuthSessionService.LoginResult("session-token", account));

        String cookie = controller.login(new AuthController.LoginRequest("creator", "password", false), request)
                .getHeaders().getFirst(HttpHeaders.SET_COOKIE);

        assertThat(cookie).contains("GN_SESSION=session-token", "HttpOnly", "SameSite=Lax")
                .doesNotContain("Max-Age", "Expires=");
    }

    @Test
    void secureRequestMarksAuthenticationCookieSecure() {
        when(request.getHeader("User-Agent")).thenReturn("Browser");
        when(request.isSecure()).thenReturn(true);
        when(sessions.login("creator", "password", "Browser", true))
                .thenReturn(new AuthSessionService.LoginResult("secure-token", account));

        String cookie = controller.login(new AuthController.LoginRequest("creator", "password", true), request)
                .getHeaders().getFirst(HttpHeaders.SET_COOKIE);

        assertThat(cookie).contains("Secure");
    }

    @Test
    void serverSessionLifetimeMatchesRememberChoice() {
        assertThat(AuthSessionService.sessionLifetime(false)).isEqualTo(Duration.ofHours(12));
        assertThat(AuthSessionService.sessionLifetime(true)).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void deploymentPolicyCanForceSecureCookieBehindTlsProxy() {
        AuthController proxyController=new AuthController(sessions,current,true);
        when(request.getHeader("User-Agent")).thenReturn("Browser");
        when(sessions.login("creator", "password", "Browser", false))
                .thenReturn(new AuthSessionService.LoginResult("proxy-token", account));

        String cookie=proxyController.login(new AuthController.LoginRequest("creator","password",false),request)
                .getHeaders().getFirst(HttpHeaders.SET_COOKIE);

        assertThat(cookie).contains("Secure");
    }
}
