package cn.longer233.gamenarrator.mobile;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "asset", indices = @Index(value = "uri", unique = true))
public final class AssetEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @NonNull public String uri = "";
    @NonNull public String name = "";
    @ColumnInfo(name = "media_type") @NonNull public String mediaType = "";
    @NonNull public String tags = "";
    @ColumnInfo(name = "created_at") public long createdAt;
}
