package cn.longer233.gamenarrator.mobile;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "timeline_clip")
public final class TimelineClipEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @ColumnInfo(name = "project_id") public long projectId;
    public int position;
    @ColumnInfo(name = "clip_key") @NonNull public String clipKey = "";
    @NonNull public String uri = "";
    @NonNull public String name = "";
    @ColumnInfo(name = "start_ms") public long startMs;
    @ColumnInfo(name = "end_ms") public long endMs;
    public int muted;
    @NonNull public String subtitle = "";
    @NonNull public String narration = "";
    @ColumnInfo(name = "effect_cue") @NonNull public String effectCue = "";
    @NonNull public String track = "V1";
}
