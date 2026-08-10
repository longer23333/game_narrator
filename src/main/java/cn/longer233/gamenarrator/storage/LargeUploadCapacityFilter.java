package cn.longer233.gamenarrator.storage;

import cn.longer233.gamenarrator.observability.InsufficientStorageException;
import cn.longer233.gamenarrator.observability.StorageCapacityGuard;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class LargeUploadCapacityFilter extends OncePerRequestFilter {
    private final StorageCapacityGuard capacity;
    public LargeUploadCapacityFilter(StorageCapacityGuard capacity) { this.capacity = capacity; }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String type = request.getContentType();
        return !"POST".equalsIgnoreCase(request.getMethod()) || !"/api/tasks".equals(request.getRequestURI())
                || type == null || !type.toLowerCase().startsWith("multipart/form-data");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        try {
            long contentLength = request.getContentLengthLong();
            if (contentLength > 0) capacity.requireUploadCapacity(contentLength);
            chain.doFilter(request, response);
        } catch (InsufficientStorageException exception) {
            response.setStatus(507);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"INSUFFICIENT_STORAGE\",\"message\":\"磁盘空间不足，无法安全接收该视频\",\"suggestion\":\"清理存储或更换容量更大的存储目录\"}");
        }
    }
}
