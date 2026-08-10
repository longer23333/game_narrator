package cn.longer233.gamenarrator.mobile;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface EditHistoryDao {
    @Insert long insert(EditHistoryEntity entry);

    @Query("SELECT payload FROM edit_history WHERE project_id=:projectId ORDER BY id DESC LIMIT :limit")
    List<String> recentPayloads(long projectId, int limit);

    @Query("DELETE FROM edit_history WHERE project_id=:projectId")
    void clear(long projectId);

    @Query("SELECT COUNT(*) FROM edit_history WHERE project_id=:projectId")
    int count(long projectId);

    @Query("DELETE FROM edit_history WHERE project_id=:projectId AND id NOT IN "
            + "(SELECT id FROM edit_history WHERE project_id=:projectId ORDER BY id DESC LIMIT :keep)")
    void trim(long projectId, int keep);

    @Query("DELETE FROM edit_history")
    void clearAll();
}
