package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * Room mapping of the mobile schema. The production stores currently use the
 * proven SQLiteOpenHelper path; this database is the designed migration target
 * and is exercised by instrumentation tests.
 */
@Database(entities = {ProjectEntity.class, TimelineClipEntity.class, AssetEntity.class,
        ExportJobEntity.class, EditHistoryEntity.class}, version = 1, exportSchema = false)
public abstract class GameNarratorRoomDatabase extends RoomDatabase {
    public abstract ProjectDao projectDao();
    public abstract TimelineClipDao timelineClipDao();
    public abstract AssetDao assetDao();
    public abstract ExportJobDao exportJobDao();
    public abstract EditHistoryDao editHistoryDao();

    public static GameNarratorRoomDatabase inMemory(Context context) {
        return Room.inMemoryDatabaseBuilder(context.getApplicationContext(),
                GameNarratorRoomDatabase.class).allowMainThreadQueries().build();
    }

    public static GameNarratorRoomDatabase open(Context context) {
        return Room.databaseBuilder(context.getApplicationContext(),
                GameNarratorRoomDatabase.class, "game-narrator-room.db").build();
    }
}
