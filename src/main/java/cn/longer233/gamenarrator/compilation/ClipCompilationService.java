package cn.longer233.gamenarrator.compilation;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ClipCompilationService {
    private final JdbcClient jdbc;

    public ClipCompilationService(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Transactional
    public CompilationView create(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isBlank() || normalized.length() > 120) throw new IllegalArgumentException("合集名称长度必须为 1 到 120 个字符");
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.sql("INSERT INTO clip_compilation(id,name,created_at) VALUES (?,?,?)")
                .params(id, normalized, now).update();
        return new CompilationView(id, normalized, now, List.of());
    }

    public List<CompilationView> list() {
        return jdbc.sql("SELECT id,name,created_at FROM clip_compilation ORDER BY created_at DESC")
                .query((rs, row) -> view((UUID) rs.getObject("id"), rs.getString("name"),
                        rs.getTimestamp("created_at").toInstant())).list();
    }

    public CompilationView find(UUID id) {
        return jdbc.sql("SELECT id,name,created_at FROM clip_compilation WHERE id=?").param(id)
                .query((rs, row) -> view(id, rs.getString("name"), rs.getTimestamp("created_at").toInstant()))
                .optional().orElseThrow(() -> new IllegalArgumentException("合集不存在"));
    }

    @Transactional
    public CompilationView add(UUID id, UUID taskId, int clipIndex) {
        find(id);
        Integer position = jdbc.sql("SELECT COALESCE(MAX(position),0)+1 FROM clip_compilation_item WHERE compilation_id=?")
                .param(id).query(Integer.class).single();
        jdbc.sql("INSERT INTO clip_compilation_item(id,compilation_id,task_id,clip_index,position,created_at) VALUES (?,?,?,?,?,?)")
                .params(UUID.randomUUID(), id, taskId, clipIndex, position, Instant.now()).update();
        return find(id);
    }

    @Transactional
    public CompilationView reorder(UUID id, List<UUID> itemIds) {
        CompilationView current = find(id);
        if (itemIds == null || itemIds.size() != current.items().size()
                || !java.util.Set.copyOf(itemIds).equals(current.items().stream().map(CompilationItemView::id).collect(java.util.stream.Collectors.toSet()))) {
            throw new IllegalArgumentException("排序必须包含合集中的全部切片且不能重复");
        }
        for (int i = 0; i < itemIds.size(); i++) {
            jdbc.sql("UPDATE clip_compilation_item SET position=? WHERE id=? AND compilation_id=?")
                    .params(-(i + 1), itemIds.get(i), id).update();
        }
        for (int i = 0; i < itemIds.size(); i++) {
            jdbc.sql("UPDATE clip_compilation_item SET position=? WHERE id=? AND compilation_id=?")
                    .params(i + 1, itemIds.get(i), id).update();
        }
        return find(id);
    }

    private CompilationView view(UUID id, String name, Instant createdAt) {
        List<CompilationItemView> items = jdbc.sql("""
                SELECT i.id,i.task_id,i.clip_index,i.position,t.name task_name
                FROM clip_compilation_item i JOIN video_tasks t ON t.id=i.task_id
                WHERE i.compilation_id=? ORDER BY i.position
                """).param(id).query((rs, row) -> new CompilationItemView((UUID) rs.getObject("id"),
                (UUID) rs.getObject("task_id"), rs.getInt("clip_index"), rs.getInt("position"),
                rs.getString("task_name"))).list();
        return new CompilationView(id, name, createdAt, items);
    }

    public record CompilationView(UUID id, String name, Instant createdAt, List<CompilationItemView> items) {}
    public record CompilationItemView(UUID id, UUID taskId, int clipIndex, int position, String taskName) {}
}
