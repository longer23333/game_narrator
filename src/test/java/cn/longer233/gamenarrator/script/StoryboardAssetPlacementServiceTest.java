package cn.longer233.gamenarrator.script;

import cn.longer233.gamenarrator.asset.AssetCatalogService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoryboardAssetPlacementServiceTest {
    @Test
    void usesOnlyWikimediaForAutomaticVideoPlacement() {
        AssetCatalogService assets = mock(AssetCatalogService.class);
        when(assets.supportsProvider("WIKIMEDIA", "VIDEO")).thenReturn(true);
        StoryboardAssetPlacementService service = new StoryboardAssetPlacementService(null, null, assets, null);

        assertThat(service.providersFor("VIDEO")).isEqualTo(List.of("WIKIMEDIA"));
    }
}
