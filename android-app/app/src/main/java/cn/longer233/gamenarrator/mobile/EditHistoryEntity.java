package cn.longer233.gamenarrator.mobile;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "edit_history")
public final class EditHistoryEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @ColumnInfo(name = "project_id") public long projectId;
    @NonNull public String payload = "";
    @ColumnInfo(name = "created_at") public long createdAt;
}
