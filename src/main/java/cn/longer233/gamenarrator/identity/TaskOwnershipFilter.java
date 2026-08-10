package cn.longer233.gamenarrator.identity;

import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Enforces task ownership once for every task-scoped HTTP endpoint. Background workers remain system-scoped. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TaskOwnershipFilter extends OncePerRequestFilter {
    private static final Pattern TASK_PATH = Pattern.compile("^/api/tasks/([0-9a-fA-F-]{36})(?:/|$)");
    private static final Pattern SEGMENT_PATH = Pattern.compile("^/api/video-segments/([0-9a-fA-F-]{36})(?:/|$)");
    private final VideoTaskRepository tasks;
    private final CurrentUserContext current;
    public TaskOwnershipFilter(VideoTaskRepository tasks, CurrentUserContext current) { this.tasks = tasks; this.current = current; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        UUID taskId = taskId(request.getRequestURI());
        if (taskId != null && !tasks.existsByIdAndOwnerId(taskId, current.userId())) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"TASK_NOT_FOUND\",\"message\":\"任务不存在\"}");
            return;
        }
        chain.doFilter(request, response);
    }
    static UUID taskId(String path) {
        for (Pattern pattern : new Pattern[]{TASK_PATH, SEGMENT_PATH}) {
            Matcher matcher = pattern.matcher(path == null ? "" : path);
            if (matcher.find()) try { return UUID.fromString(matcher.group(1)); } catch (IllegalArgumentException ignored) { return null; }
        }
        return null;
    }
}
