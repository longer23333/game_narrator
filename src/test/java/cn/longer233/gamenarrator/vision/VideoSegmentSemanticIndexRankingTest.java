package cn.longer233.gamenarrator.vision;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class VideoSegmentSemanticIndexRankingTest {
    @Test
    void lexicalAndEventMatchesImproveOtherwiseEqualSemanticScores() {
        double relevant = VideoSegmentSemanticIndex.hybridScore("激烈战斗", .55, "Boss 战", "BATTLE",
                "角色正在激烈战斗并反击");
        double generic = VideoSegmentSemanticIndex.hybridScore("激烈战斗", .55, "日常场景", "DIALOGUE",
                "角色站在城镇中");
        assertThat(relevant).isGreaterThan(generic);
    }

    @Test
    void nearbyFramesAreDiversifiedBeforeFillingRemainingSlots() {
        UUID task = UUID.randomUUID();
        var first = result(task, 10, .9); var duplicate = result(task, 11, .85); var distant = result(task, 20, .8);
        assertThat(VideoSegmentSemanticIndex.diversify(List.of(first, duplicate, distant), 2))
                .containsExactly(first, distant);
    }

    @Test
    void boundedCandidateQueueRetainsOnlyHighestScores() {
        PriorityQueue<Double> candidates = new PriorityQueue<>(Comparator.naturalOrder());

        for (double score : List.of(.1, .9, .4, .8, .2)) {
            VideoSegmentSemanticIndex.offerBounded(candidates, score, 3);
        }

        assertThat(candidates).containsExactlyInAnyOrder(.4, .8, .9);
    }

    private VideoSegmentSearchResult result(UUID task, double time, double score) {
        return new VideoSegmentSearchResult(task, "task", (int) time, time, "BATTLE", "description", score);
    }
}
