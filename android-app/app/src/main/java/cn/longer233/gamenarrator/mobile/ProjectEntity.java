package cn.longer233.gamenarrator.mobile;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "project")
public final class ProjectEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @NonNull public String name = "";
    @NonNull public String status = "";
    @ColumnInfo(name = "pipeline_stage") @NonNull public String pipelineStage = "IMPORT";
    public int archived;
    @ColumnInfo(name = "created_at") public long createdAt;
    @ColumnInfo(name = "updated_at") public long updatedAt;
}
