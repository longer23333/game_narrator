package cn.longer233.gamenarrator.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthSessionService sessions;
    private final CurrentUserContext current;
    private final boolean secureCookie;
    public AuthController(AuthSessionService sessions, CurrentUserContext current,
                          @Value("${game-narrator.auth.secure-cookie:false}") boolean secureCookie) {
        this.sessions = sessions; this.current = current; this.secureCookie=secureCookie;
    }

    @PostMapping("/register") public ResponseEntity<AuthView> register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
        return loggedIn(sessions.register(body.username(), body.email(), body.displayName(), body.password(), body.bootstrapToken(), client(request), body.rememberMe()), body.rememberMe(), request);
    }
    @PostMapping("/login") public ResponseEntity<AuthView> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        return loggedIn(sessions.login(body.username(), body.password(), client(request), body.rememberMe()), body.rememberMe(), request);
    }
    @PostMapping("/logout") public ResponseEntity<AuthView> logout(@CookieValue(name=AuthenticationFilter.COOKIE_NAME, required=false) String token, HttpServletRequest request) {
        sessions.revoke(sessionToken(token, request));
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO, request).toString()).body(anonymous());
    }
    @PostMapping("/anonymous") public ResponseEntity<AuthView> anonymous(@CookieValue(name=AuthenticationFilter.COOKIE_NAME, required=false) String token, HttpServletRequest request) {
        sessions.revoke(sessionToken(token, request));
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO, request).toString()).body(anonymous());
    }
    @GetMapping("/me") public AuthView me() {
        if (!current.authenticated()) return anonymous();
        return AuthView.from(sessions.account(current.userId()), true);
    }
    private ResponseEntity<AuthView> loggedIn(AuthSessionService.LoginResult result, boolean rememberMe, HttpServletRequest request) {
        Duration persistentAge = rememberMe ? Duration.ofDays(30) : null;
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie(result.token(), persistentAge, request).toString()).body(AuthView.from(result.account(), true));
    }
    private ResponseCookie cookie(String value, Duration age, HttpServletRequest request) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(AuthenticationFilter.COOKIE_NAME, value)
                .httpOnly(true).secure(secureCookie || request.isSecure()).sameSite("Lax").path("/");
        if (age != null) builder.maxAge(age);
        return builder.build();
    }
    private String client(HttpServletRequest request) {
        String agent = request.getHeader("User-Agent");
        String address = request.getRemoteAddr();
        if (address == null || address.isBlank()) return agent;
        return address + " | " + (agent == null || agent.isBlank() ? "unknown-client" : agent);
    }
    private String sessionToken(String cookie, HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7).strip() : cookie;
    }
    private AuthView anonymous() { return new AuthView(LocalUserContext.LOCAL_USER_ID.toString(), "local-user", "匿名使用", "USER", false, true); }
    public record RegisterRequest(@NotBlank String username, String email, String displayName,
                                  @NotBlank String password, String bootstrapToken, boolean rememberMe) {}
    public record LoginRequest(@NotBlank String username, @NotBlank String password, boolean rememberMe) {}
    public record AuthView(String id, String username, String displayName, String role, boolean authenticated, boolean anonymous) {
        static AuthView from(AuthSessionService.Account a, boolean authenticated) { return new AuthView(a.id().toString(), a.username(), a.displayName(), a.role(), authenticated, false); }
    }
}
