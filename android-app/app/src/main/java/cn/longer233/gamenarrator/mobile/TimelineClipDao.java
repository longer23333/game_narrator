package cn.longer233.gamenarrator.mobile;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface TimelineClipDao {
    @Insert long insert(TimelineClipEntity clip);

    @Query("SELECT * FROM timeline_clip ORDER BY position ASC")
    List<TimelineClipEntity> all();

    @Query("DELETE FROM timeline_clip")
    void clearAll();
}
