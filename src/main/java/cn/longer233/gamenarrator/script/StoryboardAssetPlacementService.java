package cn.longer233.gamenarrator.script;

import cn.longer233.gamenarrator.asset.AssetView;
import cn.longer233.gamenarrator.asset.AssetCatalogService;
import cn.longer233.gamenarrator.asset.AssetSearchRequest;
import cn.longer233.gamenarrator.task.application.TaskNotFoundException;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class StoryboardAssetPlacementService {
    private static final int MAX_AUTOMATIC_VISUALS = 8;
    private static final int MAX_AUTOMATIC_SOUND_EFFECTS = 4;
    private final JdbcTemplate jdbc;
    private final VideoTaskRepository tasks;
    private final AssetCatalogService assets;
    private final ScriptWorkspaceService workspace;

    public StoryboardAssetPlacementService(JdbcTemplate jdbc, VideoTaskRepository tasks,
                                           AssetCatalogService assets, ScriptWorkspaceService workspace) {
        this.jdbc = jdbc; this.tasks = tasks; this.assets = assets; this.workspace = workspace;
    }

    public List<StoryboardAssetPlacementView> list(UUID taskId) {
        requireTask(taskId);
        return jdbc.query("""
                SELECT p.id,p.asset_id,p.clip_index,a.title,a.asset_type,p.placement_type,
                       p.position_name,p.instruction,p.ai_assigned,p.cutout_applied
                FROM storyboard_asset_placement p JOIN external_asset a ON a.id=p.asset_id
                WHERE p.task_id=? ORDER BY p.clip_index,p.created_at
                """, (rs, n) -> new StoryboardAssetPlacementView(
                rs.getObject("id", UUID.class), rs.getObject("asset_id", UUID.class), rs.getInt("clip_index"),
                rs.getString("title"), rs.getString("asset_type"), rs.getString("placement_type"),
                rs.getString("position_name"), rs.getString("instruction"), rs.getBoolean("ai_assigned"),
                rs.getBoolean("cutout_applied"), "/api/assets/" + rs.getObject("asset_id") + "/preview"), taskId);
    }

    @Transactional
    public StoryboardAssetPlacementView place(UUID taskId, int requestedClip, PlaceStoryboardAssetRequest request) {
        requireTask(taskId);
        AssetView asset = assets.find(request.assetId());
        if (!"DOWNLOADED".equals(asset.importStatus())) throw new IllegalStateException("素材必须先下载或上传到本地");
        StoryboardView storyboard = workspace.storyboard(taskId);
        String instruction = request.instruction() == null ? "" : request.instruction().trim();
        int clip = request.aiAssign() ? chooseClip(storyboard, asset, requestedClip, instruction) : requestedClip;
        if (storyboard.segments().stream().noneMatch(item -> item.clipIndex() == clip)) {
            throw new IllegalArgumentException("分镜不存在：" + clip);
        }
        String placementType = switch (asset.assetType()) {
            case "SFX" -> "SOUND_EFFECT"; case "BGM" -> "BACKGROUND_AUDIO";
            case "MEME" -> instruction.contains("背景") ? "BACKGROUND" : "OVERLAY";
            default -> instruction.contains("背景") ? "BACKGROUND" : "OVERLAY";
        };
        String position = inferPosition(instruction, placementType);
        boolean cutout = asset.tags().stream().anyMatch(tag -> tag.name().contains("已抠图"));
        UUID id = UUID.randomUUID();
        jdbc.update("""
                MERGE INTO storyboard_asset_placement(id,task_id,clip_index,asset_id,placement_type,
                position_name,instruction,ai_assigned,cutout_applied,created_at) KEY(task_id,clip_index,asset_id)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """, id, taskId, clip, asset.id(), placementType, position, instruction,
                request.aiAssign(), cutout, OffsetDateTime.now());
        return list(taskId).stream().filter(item -> item.assetId().equals(asset.id()) && item.clipIndex() == clip)
                .findFirst().orElseThrow();
    }

    @Transactional
    public void delete(UUID taskId, UUID placementId) {
        requireTask(taskId);
        if (jdbc.update("DELETE FROM storyboard_asset_placement WHERE id=? AND task_id=?", placementId, taskId) == 0)
            throw new IllegalArgumentException("分镜素材不存在");
    }

    @Transactional
    public StoryboardAssetPlacementView update(UUID taskId, UUID placementId, UpdateStoryboardAssetRequest request) {
        requireTask(taskId);
        int changed = jdbc.update("""
                UPDATE storyboard_asset_placement SET position_name=?,cutout_applied=?,instruction=?
                WHERE id=? AND task_id=?
                """, request.position(), request.cutoutApplied(),
                request.instruction() == null ? "" : request.instruction().trim(), placementId, taskId);
        if (changed == 0) throw new IllegalArgumentException("分镜素材不存在");
        return list(taskId).stream().filter(item -> item.id().equals(placementId)).findFirst().orElseThrow();
    }

    public AutoAssetAssignmentView autoAssign(UUID taskId) {
        requireTask(taskId);
        StoryboardView board = workspace.storyboard(taskId);
        List<String> warnings = new ArrayList<>();
        List<StoryboardAssetPlacementView> existing = list(taskId);
        int initialCount = existing.size();
        Set<UUID> used = new HashSet<>();
        for (StoryboardAssetPlacementView placement : existing) used.add(placement.assetId());
        boolean bgmAssigned = existing.stream().anyMatch(item -> "BACKGROUND_AUDIO".equals(item.placementType()));
        int visualCount = (int) existing.stream().filter(item -> !isAudioPlacement(item)).count();
        int soundEffectCount = (int) existing.stream().filter(item -> "SOUND_EFFECT".equals(item.placementType())).count();
        for (StoryboardSegmentView segment : board.segments()) {
            String query = String.join(" ", Objects.toString(segment.eventType(), ""),
                    Objects.toString(segment.description(), ""), Objects.toString(segment.effectCue(), ""),
                    Objects.toString(segment.subtitle(), ""));
            boolean hasVisual = existing.stream().anyMatch(item -> item.clipIndex() == segment.clipIndex()
                    && !isAudioPlacement(item));
            AssetView visual = hasVisual || visualCount >= MAX_AUTOMATIC_VISUALS ? null
                    : findOrDownload(query, List.of("VIDEO", "MEME"), used, warnings);
            if (visual != null) {
                place(taskId, segment.clipIndex(), new PlaceStoryboardAssetRequest(visual.id(),
                        "自动匹配分镜；视频自动按镜头时长剪切，图片自动循环并裁切适配；透明或绿幕素材自动抠图", false));
                used.add(visual.id());
                visualCount++;
            }
            boolean hasSfx = existing.stream().anyMatch(item -> item.clipIndex() == segment.clipIndex()
                    && "SOUND_EFFECT".equals(item.placementType()));
            if (!hasSfx && soundEffectCount < MAX_AUTOMATIC_SOUND_EFFECTS && segment.finalScore() >= 65) {
                AssetView sfx = findOrDownload(query + " 音效", List.of("SFX"), used, warnings);
                if (sfx != null) {
                    place(taskId, segment.clipIndex(), new PlaceStoryboardAssetRequest(sfx.id(), "关键事件自动音效", false));
                    used.add(sfx.id());
                    soundEffectCount++;
                }
            }
            if (!bgmAssigned) {
                AssetView bgm = findOrDownload(query + " 背景音乐", List.of("BGM"), used, warnings);
                if (bgm != null) {
                    place(taskId, segment.clipIndex(), new PlaceStoryboardAssetRequest(bgm.id(), "全片低音量背景音乐", false));
                    used.add(bgm.id()); bgmAssigned = true;
                }
            }
        }
        List<StoryboardAssetPlacementView> result = list(taskId);
        if (result.size() == initialCount && result.isEmpty()) warnings.add("没有找到许可允许自动使用且可下载的匹配素材");
        return new AutoAssetAssignmentView(Math.max(0, result.size() - initialCount), true,
                warnings.stream().distinct().limit(12).toList(), result);
    }

    public AutoAssetAssignmentView autoAssignIfEmpty(UUID taskId) {
        List<StoryboardAssetPlacementView> current = list(taskId);
        return current.isEmpty() ? autoAssign(taskId)
                : new AutoAssetAssignmentView(current.size(), true, List.of(), current);
    }

    private AssetView findOrDownload(String query, List<String> types, Set<UUID> used, List<String> warnings) {
        for (String type : types) {
            List<AssetView> local = assets.list(type, query, null, "DOWNLOADED", null, false,
                    "newest", true, 12);
            Optional<AssetView> available = local.stream().filter(item -> !used.contains(item.id()))
                    .max(Comparator.comparingInt(item -> suitability(item, query)));
            if (available.isPresent()) return available.get();
            for (String provider : providersFor(type)) {
                try {
                    List<AssetView> found = assets.discover(new AssetSearchRequest(query, type, 8, 1,
                            true, true, provider, "RELEVANCE"));
                    for (AssetView candidate : found) {
                        if (used.contains(candidate.id())) continue;
                        try { return "DOWNLOADED".equals(candidate.importStatus()) ? candidate : assets.download(candidate.id()); }
                        catch (Exception exception) { warnings.add(type + "/" + provider + "：" + concise(exception.getMessage())); }
                    }
                } catch (Exception exception) {
                    warnings.add(type + "/" + provider + "：" + concise(exception.getMessage()));
                }
            }
        }
        return null;
    }

    private boolean isAudioPlacement(StoryboardAssetPlacementView item) {
        return "BACKGROUND_AUDIO".equals(item.placementType()) || "SOUND_EFFECT".equals(item.placementType());
    }

    private int suitability(AssetView asset, String query) {
        String haystack = (Objects.toString(asset.title(), "") + " "
                + Objects.toString(asset.localizedTitle(), "") + " "
                + asset.tags().stream().map(AssetView.TagView::name).reduce("", (a, b) -> a + " " + b))
                .toLowerCase(Locale.ROOT);
        return Arrays.stream(Objects.toString(query, "").toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(word -> word.length() >= 2 && haystack.contains(word))
                .mapToInt(String::length).sum();
    }

    private List<String> providersFor(String type) {
        return switch (type) {
            case "VIDEO" -> List.of("PEXELS", "PIXABAY", "WIKIMEDIA");
            case "MEME" -> List.of("OPENVERSE", "WIKIMEDIA");
            default -> List.of("OPENVERSE", "PIXABAY");
        };
    }

    private String concise(String message) {
        if (message == null) return "不可用";
        return message.substring(0, Math.min(160, message.length()));
    }

    private int chooseClip(StoryboardView board, AssetView asset, int fallback, String instruction) {
        java.util.regex.Matcher number = java.util.regex.Pattern.compile("第?\\s*(\\d+)\\s*(?:个|镜|分镜)").matcher(instruction);
        if (number.find()) return Integer.parseInt(number.group(1));
        Set<String> words = new LinkedHashSet<>();
        words.add(asset.title());
        asset.tags().forEach(tag -> words.add(tag.name()));
        return board.segments().stream().max(Comparator.comparingInt(segment -> {
            String text = (segment.narration() + " " + segment.subtitle() + " " + segment.description() + " " + segment.eventType()).toLowerCase();
            return words.stream().filter(Objects::nonNull).map(String::toLowerCase)
                    .mapToInt(word -> !word.isBlank() && text.contains(word) ? word.length() : 0).sum();
        })).filter(best -> score(best, words) > 0).map(StoryboardSegmentView::clipIndex).orElse(fallback);
    }

    private int score(StoryboardSegmentView segment, Set<String> words) {
        String text = (segment.narration() + segment.subtitle() + segment.description() + segment.eventType()).toLowerCase();
        return words.stream().filter(Objects::nonNull).map(String::toLowerCase)
                .mapToInt(word -> !word.isBlank() && text.contains(word) ? word.length() : 0).sum();
    }

    private String inferPosition(String text, String type) {
        if (type.contains("AUDIO") || type.equals("SOUND_EFFECT")) return "AUDIO_TRACK";
        if (text.contains("左上")) return "TOP_LEFT"; if (text.contains("右上")) return "TOP_RIGHT";
        if (text.contains("左下")) return "BOTTOM_LEFT"; if (text.contains("右下")) return "BOTTOM_RIGHT";
        if (text.contains("背景") || type.equals("BACKGROUND")) return "FULL_SCREEN";
        return "CENTER";
    }

    private void requireTask(UUID id) { if (!tasks.existsById(id)) throw new TaskNotFoundException(id); }
}
