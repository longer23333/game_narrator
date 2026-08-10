package cn.longer233.gamenarrator.mobile;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "export_job")
public final class ExportJobEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @ColumnInfo(name = "project_id") public long projectId;
    @NonNull public String status = "";
    public int progress;
    @ColumnInfo(name = "output_path") @NonNull public String outputPath = "";
    @NonNull public String error = "";
    @ColumnInfo(name = "preset_label") @NonNull public String presetLabel = "";
    @ColumnInfo(name = "created_at") public long createdAt;
    @ColumnInfo(name = "updated_at") public long updatedAt;
}
