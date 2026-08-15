package cn.longer233.gamenarrator.task.application;

import cn.longer233.gamenarrator.identity.CurrentUserContext;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class TaskTrashService {
    private final JdbcTemplate jdbc;
    private final CurrentUserContext current;
    private final VideoTaskRepository repository;
    private final TaskFileCleanupService cleanup;
    private final VideoTaskViewMapper viewMapper;

    public TaskTrashService(JdbcTemplate jdbc, CurrentUserContext current, VideoTaskRepository repository,
                            TaskFileCleanupService cleanup, VideoTaskViewMapper viewMapper) {
        this.jdbc=jdbc; this.current=current; this.repository=repository; this.cleanup=cleanup;
        this.viewMapper=viewMapper;
    }

    public List<TrashedTask> list() {
        return jdbc.query("SELECT id,name,status,created_at,deleted_at FROM video_tasks WHERE owner_id=? AND deleted_at IS NOT NULL ORDER BY deleted_at DESC",
                (rs,n)->new TrashedTask(rs.getObject("id",UUID.class),rs.getString("name"),rs.getString("status"),
                        rs.getObject("created_at",OffsetDateTime.class),rs.getObject("deleted_at",OffsetDateTime.class)), current.userId());
    }

    @Transactional
    public VideoTaskView restore(UUID id) {
        int changed=jdbc.update("UPDATE video_tasks SET deleted_at=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=? AND owner_id=? AND deleted_at IS NOT NULL",id,current.userId());
        if(changed==0) throw new IllegalArgumentException("回收站中不存在该任务");
        jdbc.update("UPDATE video_project SET deleted_at=NULL,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND owner_id=?",id,current.userId());
        return viewMapper.from(repository.findByIdAndOwnerId(id,current.userId()).orElseThrow());
    }

    @Transactional
    public void purge(UUID id) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT source_video_path FROM video_tasks WHERE id=? AND owner_id=? AND deleted_at IS NOT NULL",id,current.userId());
        if(rows.isEmpty()) throw new IllegalArgumentException("回收站中不存在该任务");
        Map<String,Object> row=lower(rows.getFirst());
        List<String> paths=new ArrayList<>();
        String source=text(row.get("source_video_path"));
        long references=source==null?0:Optional.ofNullable(jdbc.queryForObject("SELECT COUNT(*) FROM video_tasks WHERE source_video_path=? AND id<>?",Long.class,source,id)).orElse(0L);
        paths.addAll(jdbc.query("""
                SELECT a.storage_key FROM artifact a
                JOIN video_tasks t ON t.project_id=a.project_id
                WHERE t.id=? AND a.deleted_at IS NULL
                """, (rs,n)->rs.getString(1), id));
        if(source!=null&&references==0)paths.add(source);
        if(source!=null&&references==0){Path file=Path.of(source);paths.add(file.resolveSibling(file.getFileName()+".platform.srt").toString());paths.add(file.resolveSibling(file.getFileName()+".platform.txt").toString());paths.add(file.resolveSibling(file.getFileName()+".platform.srt.analysis.json").toString());}
        jdbc.update("DELETE FROM processing_stages WHERE task_id=?",id);
        cleanup.enqueue(id, paths);
        jdbc.update("DELETE FROM video_tasks WHERE id=? AND owner_id=?",id,current.userId());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){cleanup.process(id);}});
    }

    private Map<String,Object> lower(Map<String,Object> source){Map<String,Object> result=new HashMap<>();source.forEach((k,v)->result.put(k.toLowerCase(Locale.ROOT),v));return result;}
    private String text(Object value){return value==null||value.toString().isBlank()?null:value.toString();}
    public record TrashedTask(UUID id,String name,String status,OffsetDateTime createdAt,OffsetDateTime deletedAt) {}
}
