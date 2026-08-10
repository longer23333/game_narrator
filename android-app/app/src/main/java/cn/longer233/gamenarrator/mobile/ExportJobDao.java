package cn.longer233.gamenarrator.mobile;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface ExportJobDao {
    @Insert long insert(ExportJobEntity job);

    @Query("SELECT * FROM export_job ORDER BY created_at DESC")
    List<ExportJobEntity> all();

    @Query("DELETE FROM export_job")
    void clearAll();
}
