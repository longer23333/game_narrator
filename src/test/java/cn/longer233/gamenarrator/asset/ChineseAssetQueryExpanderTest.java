package cn.longer233.gamenarrator.asset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChineseAssetQueryExpanderTest {
    @Test
    void expandsConfiguredChineseIntentWithoutCallingAi() {
        AssetLibraryProperties library = new AssetLibraryProperties();
        AssetLibraryProperties.QueryExpansion properties = new AssetLibraryProperties.QueryExpansion();
        AssetLibraryProperties.QueryExpansion.Synonym synonym = new AssetLibraryProperties.QueryExpansion.Synonym();
        synonym.setIntent("欢快");
        synonym.setTerms("happy upbeat cheerful");
        properties.setSynonyms(List.of(synonym));
        library.setQueryExpansion(properties);
        ChineseAssetQueryExpander expander = new ChineseAssetQueryExpander(
                new ObjectMapper(), "http://127.0.0.1:1", "unused", library);

        AssetSearchExpansion result = expander.expand("欢快背景", "BGM");

        assertThat(result.originalQuery()).isEqualTo("欢快背景");
        assertThat(result.providerQuery()).isEqualTo("happy upbeat cheerful");
        assertThat(result.chineseTags()).containsExactly("欢快");
    }

    @Test
    void leavesEnglishQueryUntouched() {
        AssetLibraryProperties library = new AssetLibraryProperties();
        ChineseAssetQueryExpander expander = new ChineseAssetQueryExpander(
                new ObjectMapper(), "http://127.0.0.1:1", "unused", library);

        AssetSearchExpansion result = expander.expand("upbeat music", "BGM");

        assertThat(result.providerQuery()).isEqualTo("upbeat music");
        assertThat(result.chineseTags()).isEmpty();
    }
}
