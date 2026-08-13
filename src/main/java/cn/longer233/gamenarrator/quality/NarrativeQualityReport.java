package cn.longer233.gamenarrator.quality;

import java.util.List;
import java.util.UUID;

public record NarrativeQualityReport(UUID taskId, int score, boolean passed, List<Issue> issues,
                                     int confirmedFactCount, int supportedSegmentCount,
                                     int segmentCount, double evidenceCoverage,
                                     double averageEventConfidence, double speakerCoverage,
                                     double ocrEvidenceCoverage, double knowledgeEvidenceCoverage,
                                     String summary) {
    public record Issue(String type, String severity, Integer clipIndex, String message, String evidence) { }
}
