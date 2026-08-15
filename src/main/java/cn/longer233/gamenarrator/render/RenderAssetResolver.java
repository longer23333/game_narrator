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
        List<java.util.UUID> taskIds = jdbc.query("""
                SELECT t.id FROM video_tasks t JOIN artifact a ON a.project_id=t.project_id
                WHERE a.artifact_type='TIMELINE_MANIFEST' AND a.deleted_at IS NULL AND a.storage_key=?
                """,
                (rs, n) -> rs.getObject(1, java.util.UUID.class), timelinePath.toAbsolutePath().normalize().toString());
        if (taskIds.isEmpty()) return List.of();
        return jdbc.query("""
                SELECT p.clip_index,p.placement_type,p.position_name,p.cutout_applied,
                       p.start_offset_seconds,p.end_offset_seconds,p.scale_percent,p.animation_name,p.z_index,
                       p.volume_percent,p.fade_in_seconds,p.fade_out_seconds,
                       a.asset_type,a.local_path,a.title,a.license_code,a.attribution,a.provider
                FROM storyboard_asset_placement p JOIN external_asset a ON a.id=p.asset_id
                WHERE p.task_id=? AND a.import_status='DOWNLOADED' AND a.local_path IS NOT NULL
                  AND (a.provider IN ('LOCAL_UPLOAD','LOCAL_DERIVED','PROJECT')
                       OR a.license_code NOT IN ('RIGHTS_REVIEW_REQUIRED','UNKNOWN','UNLICENSED'))
                ORDER BY p.clip_index,p.z_index,p.created_at
                """, (rs, n) -> {
            Path path = Path.of(rs.getString("local_path")).toAbsolutePath().normalize();
            if (!path.startsWith(storageRoot) || !Files.isRegularFile(path)
                    || !licenseRenderable(rs.getString("provider"), rs.getString("license_code"))) return null;
            return new RenderAsset(rs.getInt("clip_index"), rs.getString("asset_type"),
                    rs.getString("placement_type"), rs.getString("position_name"),
                    rs.getBoolean("cutout_applied"), path, rs.getString("title"),
                    rs.getDouble("start_offset_seconds"), (Double) rs.getObject("end_offset_seconds"),
                    rs.getInt("scale_percent"), rs.getString("animation_name"), rs.getInt("z_index"),
                    rs.getInt("volume_percent"), rs.getDouble("fade_in_seconds"), rs.getDouble("fade_out_seconds"),
                    rs.getString("license_code"), rs.getString("attribution"));
        }, taskIds.getFirst()).stream().filter(java.util.Objects::nonNull).toList();
    }

    static boolean licenseRenderable(String provider, String licenseCode) {
        if (List.of("LOCAL_UPLOAD", "LOCAL_DERIVED", "PROJECT").contains(String.valueOf(provider))) return true;
        return licenseCode != null && !licenseCode.isBlank()
                && !List.of("RIGHTS_REVIEW_REQUIRED", "UNKNOWN", "UNLICENSED").contains(licenseCode);
    }

    public record RenderAsset(int clipIndex, String assetType, String placementType, String position,
                              boolean cutoutApplied, Path path, String title, double startOffsetSeconds,
                              Double endOffsetSeconds, int scalePercent, String animation, int zIndex,
                              int volumePercent, double fadeInSeconds, double fadeOutSeconds,
                              String licenseCode, String attribution) {
        public RenderAsset(int clipIndex, String assetType, String placementType, String position,
                boolean cutoutApplied, Path path, String title, double startOffsetSeconds,
                Double endOffsetSeconds, int scalePercent, String animation, int zIndex) {
            this(clipIndex, assetType, placementType, position, cutoutApplied, path, title,
                    startOffsetSeconds, endOffsetSeconds, scalePercent, animation, zIndex,
                    48, 0, 0, "USER_AUTHORIZED", "");
        }
        public RenderAsset(int clipIndex, String assetType, String placementType, String position,
                boolean cutoutApplied, Path path, String title) {
            this(clipIndex, assetType, placementType, position, cutoutApplied, path, title,
                    0, null, 38, "NONE", 0);
        }
        public boolean audio() { return "SFX".equals(assetType) || "BGM".equals(assetType); }
    }
}
