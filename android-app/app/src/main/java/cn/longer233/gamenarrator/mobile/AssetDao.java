package cn.longer233.gamenarrator.mobile;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface AssetDao {
    @Insert long insert(AssetEntity asset);

    @Query("SELECT * FROM asset ORDER BY created_at DESC")
    List<AssetEntity> all();

    @Query("DELETE FROM asset")
    void clearAll();
}
