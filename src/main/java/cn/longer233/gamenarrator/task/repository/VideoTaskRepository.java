package cn.longer233.gamenarrator.task.repository;

import cn.longer233.gamenarrator.task.domain.VideoTask;
import cn.longer233.gamenarrator.task.domain.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
