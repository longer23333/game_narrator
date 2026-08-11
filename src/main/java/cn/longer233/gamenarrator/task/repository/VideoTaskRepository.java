package cn.longer233.gamenarrator.task.repository;

import cn.longer233.gamenarrator.task.domain.VideoTask;
import cn.longer233.gamenarrator.task.domain.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Collection;
import java.util.UUID;
import java.util.Optional;

public interface VideoTaskRepository extends JpaRepository<VideoTask, UUID> {
    List<VideoTask> findByStatus(TaskStatus status);
    List<VideoTask> findByStatusIn(Collection<TaskStatus> statuses);
    long countByStatus(TaskStatus status);
    long countBySourceVideoPath(String sourceVideoPath);
    List<VideoTask> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
    Optional<VideoTask> findByIdAndOwnerId(UUID id, UUID ownerId);
    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);

    @Query("select task.id from VideoTask task where task.ownerId = :ownerId order by task.createdAt desc")
    List<UUID> findIdsByOwnerIdOrderByCreatedAtDesc(@Param("ownerId") UUID ownerId);

    @Query("""
            select new cn.longer233.gamenarrator.task.repository.TaskListRevision(
                count(distinct task.id), max(task.updatedAt), max(stage.updatedAt))
            from VideoTask task left join task.stages stage
            where task.ownerId = :ownerId
            """)
    TaskListRevision taskListRevision(@Param("ownerId") UUID ownerId);
}
