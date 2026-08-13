package cn.longer233.gamenarrator.render;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class RenderAssetResolver {
    private final JdbcTemplate jdbc;
    private final Path storageRoot;

    public RenderAssetResolver(JdbcTemplate jdbc, @Value("${game-narrator.storage-root}") String storageRoot) {
        this.jdbc = jdbc;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public List<RenderAsset> resolve(Path timelinePath) {
        List<java.util.UUID> taskIds = jdbc.query("SELECT id FROM video_tasks WHERE timeline_path=?",
                (rs, n) -> rs.getObject(1, java.util.UUID.class), timelinePath.toAbsolutePath().normalize().toString());
        if (taskIds.isEmpty()) return List.of();
        return jdbc.query("""
                SELECT p.clip_index,p.placement_type,p.position_name,p.cutout_applied,
                       p.start_offset_seconds,p.end_offset_seconds,p.scale_percent,p.animation_name,p.z_index,
                       a.asset_type,a.local_path,a.title
                FROM storyboard_asset_placement p JOIN external_asset a ON a.id=p.asset_id
                WHERE p.task_id=? AND a.import_status='DOWNLOADED' AND a.local_path IS NOT NULL
                ORDER BY p.clip_index,p.z_index,p.created_at
                """, (rs, n) -> {
            Path path = Path.of(rs.getString("local_path")).toAbsolutePath().normalize();
            if (!path.startsWith(storageRoot) || !Files.isRegularFile(path)) return null;
            return new RenderAsset(rs.getInt("clip_index"), rs.getString("asset_type"),
                    rs.getString("placement_type"), rs.getString("position_name"),
                    rs.getBoolean("cutout_applied"), path, rs.getString("title"),
                    rs.getDouble("start_offset_seconds"), (Double) rs.getObject("end_offset_seconds"),
                    rs.getInt("scale_percent"), rs.getString("animation_name"), rs.getInt("z_index"));
        }, taskIds.getFirst()).stream().filter(java.util.Objects::nonNull).toList();
    }

    public record RenderAsset(int clipIndex, String assetType, String placementType, String position,
                              boolean cutoutApplied, Path path, String title, double startOffsetSeconds,
                              Double endOffsetSeconds, int scalePercent, String animation, int zIndex) {
        public RenderAsset(int clipIndex, String assetType, String placementType, String position,
                boolean cutoutApplied, Path path, String title) {
            this(clipIndex, assetType, placementType, position, cutoutApplied, path, title,
                    0, null, 38, "NONE", 0);
        }
        public boolean audio() { return "SFX".equals(assetType) || "BGM".equals(assetType); }
    }
}
