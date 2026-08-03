package cn.longer233.gamenarrator.asset;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BgeAssetSemanticSearchRankingTest {
    @Test
    void partialChineseTermCoverageRanksBetterThanUnrelatedText() {
        double matching = BgeAssetSemanticSearch.lexicalScore("搞笑猫咪表情包", "猫咪搞笑反应 meme 表情包");
        double unrelated = BgeAssetSemanticSearch.lexicalScore("搞笑猫咪表情包", "宏大管弦乐战斗配乐");
        assertThat(matching).isGreaterThan(unrelated).isGreaterThan(0);
    }
}
