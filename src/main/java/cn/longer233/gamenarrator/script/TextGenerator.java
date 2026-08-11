package cn.longer233.gamenarrator.script;

import cn.longer233.gamenarrator.ai.ModelAdapter;
import cn.longer233.gamenarrator.event.GameEventFact;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface TextGenerator extends ModelAdapter {
    GeneratedScript generate(Path highlightPath, String category, String style, String taskBrief,
                             String transcript, List<GameEventFact> confirmedFacts);
    GeneratedScript generateWithoutAi(Path highlightPath);
    ScriptSegment regenerateSegment(ScriptSegment current, String instruction,
                                    String previousNarration, String nextNarration);
    ScriptQualityReview reviewQuality(ScriptDocumentView document, Map<String, Object> manualReviews);
}
