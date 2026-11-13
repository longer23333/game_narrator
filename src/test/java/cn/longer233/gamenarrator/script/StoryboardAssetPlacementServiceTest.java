package cn.longer233.gamenarrator.script;

import cn.longer233.gamenarrator.asset.AssetCatalogService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class StoryboardAssetPlacementServiceTest {
    @Test
    void automaticAssignmentPlacesDownloadedVisualThroughPublicEntryPoint() {
        var assets = mock(AssetCatalogService.class);
        var tasks = mock(cn.longer233.gamenarrator.task.repository.VideoTaskRepository.class);
        var workspace = mock(ScriptWorkspaceService.class);
        UUID taskId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(tasks.existsById(taskId)).thenReturn(true);
        when(workspace.storyboard(taskId)).thenReturn(new StoryboardView("test", "", false, false,
                List.of(new StoryboardSegmentView(1, 0, 5, "", "battle", "", "battle", "battle", 10, false, false))));
        var asset = new cn.longer233.gamenarrator.asset.AssetView(assetId, "WIKIMEDIA", "VIDEO", "battle",
                "battle", "author", "", "", "", "CC BY-SA 4.0", "", "author", 5000L,
                "DOWNLOADED", "source.mp4", false, false, List.of(), java.time.OffsetDateTime.now());
        when(assets.list(eq("VIDEO"), anyString(), isNull(), eq("DOWNLOADED"), isNull(),
                eq(false), eq("newest"), eq(true), eq(12))).thenReturn(List.of(asset));
        var source = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:h2:mem:placement-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        org.flywaydb.core.Flyway.configure().dataSource(source).load().migrate();
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(source);
        jdbc.update("INSERT INTO video_tasks(id,name,game_category,commentary_style,target_duration_seconds,task_brief,source_video_path,status,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                taskId, "test", "ACTION", "ANIME_THEATER", 5, "test", "source.mp4", "READY", java.time.OffsetDateTime.now());
        jdbc.update("INSERT INTO external_asset(id,provider,external_id,asset_type,title,landing_url,license_code,import_status,metadata_json,discovered_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                assetId, "WIKIMEDIA", "test", "VIDEO", "battle", "https://commons.wikimedia.org", "CC BY-SA 4.0", "DOWNLOADED", "{}", java.time.OffsetDateTime.now());
        when(assets.find(assetId)).thenReturn(asset);
        var service = new StoryboardAssetPlacementService(jdbc, tasks, assets, workspace);

        assertThat(service.autoAssign(taskId).assignedCount()).isEqualTo(1);
        assertThat(service.autoAssign(taskId).assignedCount()).isZero();
        assertThat(service.list(taskId)).singleElement().satisfies(item -> {
            assertThat(item.assetId()).isEqualTo(assetId);
            assertThat(item.clipIndex()).isEqualTo(1);
        });
        verify(assets, never()).download(any());
    }

    @Test
    void usesOnlyWikimediaForAutomaticVideoPlacement() {
        AssetCatalogService assets = mock(AssetCatalogService.class);
        when(assets.supportsProvider("WIKIMEDIA", "VIDEO")).thenReturn(true);
        StoryboardAssetPlacementService service = new StoryboardAssetPlacementService(null, null, assets, null);

        assertThat(service.providersFor("VIDEO")).isEqualTo(List.of("WIKIMEDIA"));
    }

    @Test
    void stopsRetryingUnavailableProviderDuringOneAutomaticAssignment() throws Exception {
        AssetCatalogService assets = mock(AssetCatalogService.class);
        when(assets.supportsProvider(eq("WIKIMEDIA"), anyString())).thenReturn(true);
        when(assets.list(anyString(), anyString(), isNull(), eq("DOWNLOADED"), isNull(),
                eq(false), eq("newest"), eq(true), eq(12))).thenReturn(List.of());
        when(assets.discover(any())).thenThrow(new IllegalStateException("timeout"));
        StoryboardAssetPlacementService service = new StoryboardAssetPlacementService(null, null, assets, null);
        var method = StoryboardAssetPlacementService.class.getDeclaredMethod("findOrDownload", String.class,
                List.class, Set.class, List.class, Set.class);
        method.setAccessible(true);
        Set<String> unavailable = new HashSet<>();
        List<String> warnings = new ArrayList<>();

        method.invoke(service, "battle", List.of("VIDEO", "MEME"), new HashSet<UUID>(), warnings, unavailable);
        method.invoke(service, "music", List.of("BGM"), new HashSet<UUID>(), warnings, unavailable);

        verify(assets, times(1)).discover(any());
        assertThat(unavailable).containsExactly("WIKIMEDIA");
        assertThat(warnings).singleElement().asString().contains("已停止重复请求");
    }
}
