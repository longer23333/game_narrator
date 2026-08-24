package cn.longer233.gamenarrator.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Requires a pairing token for every non-loopback request when LAN companion mode is enabled. */
@Component
public final class CompanionAccessFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-GameNarrator-Companion";
    private final String token;

    public CompanionAccessFilter(@Value("${game-narrator.companion.token:}") String token) {
        this.token = token == null ? "" : token;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                               FilterChain chain) throws ServletException, IOException {
        if (isLoopback(request.getRemoteAddr())) {
            chain.doFilter(request, response);
            return;
        }
        String supplied = request.getHeader(HEADER);
        if (token.length() < 24 || supplied == null || !MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "电脑协同配对密钥无效");
            return;
        }
        chain.doFilter(request, response);
    }

    static boolean isLoopback(String address) {
        try { return address != null && InetAddress.getByName(address).isLoopbackAddress(); }
        catch (Exception ignored) { return false; }
    }
}
