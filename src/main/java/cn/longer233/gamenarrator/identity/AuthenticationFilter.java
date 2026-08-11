package cn.longer233.gamenarrator.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class AuthenticationFilter extends OncePerRequestFilter {
    public static final String COOKIE_NAME = "GN_SESSION";
    private final AuthSessionService sessions;
    private final LocalUserContext context;
    private final boolean requireLogin;
    public AuthenticationFilter(AuthSessionService sessions, LocalUserContext context,
                                @Value("${game-narrator.auth.require-login:false}") boolean requireLogin) {
        this.sessions = sessions; this.context = context; this.requireLogin = requireLogin;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String token = bearer(request);
        if (token == null && request.getCookies() != null) token = Arrays.stream(request.getCookies()).filter(c -> COOKIE_NAME.equals(c.getName())).map(Cookie::getValue).findFirst().orElse(null);
        AuthSessionService.Account account = sessions.resolve(token);
        if (account == null && requireLogin && request.getRequestURI().startsWith("/api/")
                && !request.getRequestURI().startsWith("/api/auth/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"AUTHENTICATION_REQUIRED\",\"message\":\"请先登录\"}");
            return;
        }
        if (account != null) context.begin(account.id(), account.role(), true); else context.begin(LocalUserContext.LOCAL_USER_ID, "USER", false);
        try { chain.doFilter(request, response); } finally { context.clear(); }
    }
    private String bearer(HttpServletRequest request) {
        String value = request.getHeader("Authorization");
        return value != null && value.startsWith("Bearer ") ? value.substring(7).strip() : null;
    }
}
