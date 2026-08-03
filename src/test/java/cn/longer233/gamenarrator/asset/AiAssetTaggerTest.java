package cn.longer233.gamenarrator.asset;

import com.fasterxml.jackson.databind.ObjectMapper;
import cn.longer233.gamenarrator.ai.AdaptiveAiChatClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiAssetTaggerTest {
    @Test
    void fallbackKeepsLocalizationAndAnalysisInChineseWhenModelIsUnavailable() {
        AdaptiveAiChatClient client = mock(AdaptiveAiChatClient.class);
        try {
            when(client.chatJson(any(), any(), anyBoolean(), any())).thenThrow(new IllegalStateException("AI 未配置"));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        AiAssetTagger tagger = new AiAssetTagger(new ObjectMapper(), client);

        List<AiAssetTagger.AssetAiAnalysis> result = tagger.analyzeBatch(List.of(
                new AiAssetTagger.AssetAiInput("Funny green screen reaction", "VIDEO",
                        List.of("funny", "green screen", "game"))));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().chineseTitle()).contains("绿幕");
        assertThat(result.getFirst().translatedTags()).allMatch(this::containsChinese);
        assertThat(result.getFirst().analysisTags()).allMatch(this::containsChinese);
    }

    @Test
    void unknownEnglishTitleIsKeptWhenTranslationIsUnavailable() {
        AdaptiveAiChatClient client = mock(AdaptiveAiChatClient.class);
        try {
            when(client.chatJson(any(), any(), anyBoolean(), any())).thenThrow(new IllegalStateException("AI 未配置"));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        AiAssetTagger tagger = new AiAssetTagger(new ObjectMapper(), client);
        String title = "The Woman and Miss Sweetie Poo – an Ig Nobel Prize favorite moment.webm";
        assertThat(tagger.analyzeBatch(List.of(new AiAssetTagger.AssetAiInput(title, "VIDEO", List.of())))
                .getFirst().chineseTitle()).isEqualTo(title);
    }

    private boolean containsChinese(String value) {
        return value.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }
}
