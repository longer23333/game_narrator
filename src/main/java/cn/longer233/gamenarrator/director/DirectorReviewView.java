package cn.longer233.gamenarrator.director;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
public record DirectorReviewView(UUID id, UUID taskId, String status, int requestedRounds, int completedRounds,
 String promptVersion, String modelVersion, String moderatorSummary, double consensusScore, JsonNode finalDecision,
 String userAction, String userModification, long inputTokens, long outputTokens, double estimatedCost,
 long elapsedMs, OffsetDateTime createdAt, OffsetDateTime completedAt, OffsetDateTime appliedAt, List<Message> messages) {
 public record Message(UUID id,String roleCode,String roleName,int roundNo,int sequenceNo,String messageType,JsonNode content,
  JsonNode citedEventIds,JsonNode citedClipIndexes,JsonNode citedKnowledgeRefs,long inputTokens,long outputTokens,long elapsedMs,String modelVersion,OffsetDateTime createdAt){}
}
