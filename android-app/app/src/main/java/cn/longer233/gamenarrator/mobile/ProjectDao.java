package cn.longer233.gamenarrator.mobile;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface ProjectDao {
    @Insert long insert(ProjectEntity project);

    @Query("SELECT * FROM project ORDER BY updated_at DESC")
    List<ProjectEntity> all();

    @Query("DELETE FROM project")
    void clearAll();
}
