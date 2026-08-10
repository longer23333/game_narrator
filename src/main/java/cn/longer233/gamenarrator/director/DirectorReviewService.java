package cn.longer233.gamenarrator.director;

import cn.longer233.gamenarrator.ai.AdaptiveAiChatClient;
import cn.longer233.gamenarrator.ai.AiUsageService;
import cn.longer233.gamenarrator.event.BattleNarrativePlanService;
import cn.longer233.gamenarrator.event.GameEventTimelineService;
import cn.longer233.gamenarrator.identity.CurrentUserContext;
import cn.longer233.gamenarrator.script.ScriptWorkspaceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class DirectorReviewService {
 private static final String PROMPT_VERSION="director-board-v1";
 private static final List<Role> ROLES=List.of(
  new Role("FACT_REVIEWER","事实审查员","核对所有判断是否被已确认事件和知识证据支持，指出事实风险。"),
  new Role("STORY_DIRECTOR","剧情导演","评估铺垫、危机、转折、高潮、结果是否形成完整故事。"),
  new Role("PACE_EDITOR","节奏剪辑师","评估镜头顺序、长度、信息密度、音乐强度和节奏变化。"),
  new Role("AUDIENCE_ANALYST","攻略/娱乐分析师","判断内容对攻略学习和娱乐观看的价值，提出受众取舍。"));
 private final JdbcTemplate jdbc; private final ObjectMapper mapper; private final AdaptiveAiChatClient ai;
 private final AiUsageService usage; private final CurrentUserContext current; private final GameEventTimelineService events;
 private final ScriptWorkspaceService workspace; private final BattleNarrativePlanService narrative;
 public DirectorReviewService(JdbcTemplate jdbc,ObjectMapper mapper,AdaptiveAiChatClient ai,AiUsageService usage,
  CurrentUserContext current,GameEventTimelineService events,ScriptWorkspaceService workspace,BattleNarrativePlanService narrative){this.jdbc=jdbc;this.mapper=mapper;this.ai=ai;this.usage=usage;this.current=current;this.events=events;this.workspace=workspace;this.narrative=narrative;}

 public DirectorReviewView start(UUID taskId,int requestedRounds){
  int rounds=Math.max(1,Math.min(2,requestedRounds)); events.list(taskId);
  Integer running=jdbc.queryForObject("SELECT COUNT(*) FROM ai_director_review WHERE task_id=? AND owner_id=? AND status='RUNNING'",Integer.class,taskId,current.userId());
  if(running!=null&&running>0)throw new IllegalStateException("该项目已有正在进行的导演评审会");
  var confirmed=events.list(taskId).stream().filter(e->"CONFIRMED".equals(e.confirmationStatus())).toList();
  if(confirmed.isEmpty())throw new IllegalStateException("请先确认至少一个游戏事件，再召开 AI 导演评审会");
  var board=workspace.storyboard(taskId); var basePlan=narrative.generate(taskId); UUID reviewId=UUID.randomUUID(); OffsetDateTime started=OffsetDateTime.now();
  jdbc.update("INSERT INTO ai_director_review(id,task_id,owner_id,status,requested_rounds,prompt_version,model_version,created_at) VALUES(?,?,?,'RUNNING',?,?,?,?)",reviewId,taskId,current.userId(),rounds,PROMPT_VERSION,ai.activeModel(false),started);
  Map<String,UUID> agents=new LinkedHashMap<>();
  for(Role role:ROLES){UUID id=UUID.randomUUID();agents.put(role.code,id);jdbc.update("INSERT INTO ai_director_agent(id,review_id,role_code,role_name,prompt_version,prompt_snapshot,model_version,created_at) VALUES(?,?,?,?,?,?,?,?)",id,reviewId,role.code,role.name,PROMPT_VERSION,role.instruction,ai.activeModel(false),started);}
  long began=System.nanoTime(); int sequence=1; List<JsonNode> opinions=new ArrayList<>(); AiUsageService.UsageSnapshot reviewUsageBefore=usage.snapshot();
  try{
   String facts=mapper.writeValueAsString(Map.of("confirmedEvents",confirmed,"storyboard",board,"baseNarrativePlan",basePlan));
   for(int round=1;round<=rounds;round++){
    for(Role role:ROLES){String prompt=rolePrompt(role,round,facts,opinions);AiUsageService.UsageSnapshot before=usage.snapshot();long call=System.nanoTime();JsonNode result=ai.chatJson(prompt,List.of(),false,Duration.ofMinutes(3));long ms=(System.nanoTime()-call)/1_000_000;AiUsageService.UsageSnapshot after=usage.snapshot();insertMessage(reviewId,agents.get(role.code),round,sequence++,"AGENT_OPINION",result,before,after,ms);opinions.add(result);}
   }
   String moderatorPrompt=moderatorPrompt(facts,opinions);AiUsageService.UsageSnapshot before=usage.snapshot();long call=System.nanoTime();JsonNode decision=ai.chatJson(moderatorPrompt,List.of(),false,Duration.ofMinutes(4));long ms=(System.nanoTime()-call)/1_000_000;AiUsageService.UsageSnapshot after=usage.snapshot();insertMessage(reviewId,null,rounds,sequence,"MODERATOR_DECISION",decision,before,after,ms);
   double consensus=Math.max(0,Math.min(1,decision.path("consensusScore").asDouble(.5)));String summary=decision.path("summary").asText("评审完成，等待用户确认");
   long elapsed=(System.nanoTime()-began)/1_000_000;var totals=totals(reviewId);AiUsageService.UsageSnapshot reviewUsageAfter=usage.snapshot();
   jdbc.update("UPDATE ai_director_review SET status='AWAITING_USER',completed_rounds=?,moderator_summary=?,consensus_score=?,final_decision_json=?,input_tokens=?,output_tokens=?,estimated_cost=?,elapsed_ms=?,completed_at=? WHERE id=?",rounds,summary,consensus,mapper.writeValueAsString(decision),totals[0],totals[1],Math.max(0,reviewUsageAfter.todayCost()-reviewUsageBefore.todayCost()),elapsed,OffsetDateTime.now(),reviewId);
  }catch(Exception ex){jdbc.update("UPDATE ai_director_review SET status='FAILED',moderator_summary=?,elapsed_ms=?,completed_at=? WHERE id=?",safe(ex.getMessage()),(System.nanoTime()-began)/1_000_000,OffsetDateTime.now(),reviewId);throw new IllegalStateException("AI 导演评审会执行失败："+safe(ex.getMessage()),ex);}
  return find(reviewId);
 }

 public DirectorReviewView decide(UUID id,String action,String modification){DirectorReviewView view=find(id);if(!"AWAITING_USER".equals(view.status()))throw new IllegalStateException("该评审会当前不能确认");String normalized=action==null?"":action.toUpperCase(Locale.ROOT);if(!Set.of("ACCEPTED","MODIFIED","REJECTED").contains(normalized))throw new IllegalArgumentException("操作必须是 ACCEPTED、MODIFIED 或 REJECTED");if("MODIFIED".equals(normalized)&&(modification==null||modification.isBlank()))throw new IllegalArgumentException("修改评审结果时必须填写修改说明");
  if("MODIFIED".equals(normalized)) reviseDecision(view,modification.strip());
  jdbc.update("UPDATE ai_director_review SET status=?,user_action=?,user_modification=? WHERE id=?",normalized,normalized,modification,id);return find(id);}
 @Transactional public DirectorReviewView apply(UUID id){DirectorReviewView view=find(id);if(!Set.of("ACCEPTED","MODIFIED").contains(view.status()))throw new IllegalStateException("只有用户接受或修改后的评审结果可以应用");narrative.applyReviewed(view.taskId(),view.finalDecision());jdbc.update("UPDATE ai_director_review SET status='APPLIED',applied_at=? WHERE id=?",OffsetDateTime.now(),id);return find(id);}
 public List<DirectorReviewView> list(UUID taskId){events.list(taskId);return jdbc.query("SELECT id FROM ai_director_review WHERE task_id=? AND owner_id=? ORDER BY created_at DESC",(rs,n)->find(rs.getObject(1,UUID.class)),taskId,current.userId());}
 public DirectorReviewView find(UUID id){return jdbc.query("SELECT * FROM ai_director_review WHERE id=? AND owner_id=?",(rs,n)->mapReview(rs),id,current.userId()).stream().findFirst().orElseThrow(()->new IllegalArgumentException("评审会不存在"));}
 private DirectorReviewView mapReview(java.sql.ResultSet rs)throws java.sql.SQLException{UUID id=rs.getObject("id",UUID.class);JsonNode decision=read(rs.getString("final_decision_json"));List<DirectorReviewView.Message> messages=jdbc.query("SELECT m.*,a.role_code,a.role_name FROM ai_director_message m LEFT JOIN ai_director_agent a ON a.id=m.agent_id WHERE m.review_id=? ORDER BY m.round_no,m.sequence_no",(r,n)->new DirectorReviewView.Message(r.getObject("id",UUID.class),r.getString("role_code"),r.getString("role_name"),r.getInt("round_no"),r.getInt("sequence_no"),r.getString("message_type"),read(r.getString("content_json")),read(r.getString("cited_event_ids")),read(r.getString("cited_clip_indexes")),read(r.getString("cited_knowledge_refs")),r.getLong("input_tokens"),r.getLong("output_tokens"),r.getLong("elapsed_ms"),r.getString("model_version"),r.getObject("created_at",OffsetDateTime.class)),id);return new DirectorReviewView(id,rs.getObject("task_id",UUID.class),rs.getString("status"),rs.getInt("requested_rounds"),rs.getInt("completed_rounds"),rs.getString("prompt_version"),rs.getString("model_version"),rs.getString("moderator_summary"),rs.getDouble("consensus_score"),decision,rs.getString("user_action"),rs.getString("user_modification"),rs.getLong("input_tokens"),rs.getLong("output_tokens"),rs.getDouble("estimated_cost"),rs.getLong("elapsed_ms"),rs.getObject("created_at",OffsetDateTime.class),rs.getObject("completed_at",OffsetDateTime.class),rs.getObject("applied_at",OffsetDateTime.class),messages);}
 private void insertMessage(UUID review,UUID agent,int round,int seq,String type,JsonNode result,AiUsageService.UsageSnapshot before,AiUsageService.UsageSnapshot after,long ms)throws Exception{jdbc.update("INSERT INTO ai_director_message(id,review_id,agent_id,round_no,sequence_no,message_type,content_json,cited_event_ids,cited_clip_indexes,cited_knowledge_refs,input_tokens,output_tokens,elapsed_ms,model_version,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),review,agent,round,seq,type,mapper.writeValueAsString(result),mapper.writeValueAsString(result.path("eventIds")),mapper.writeValueAsString(result.path("clipIndexes")),mapper.writeValueAsString(result.path("knowledgeRefs")),Math.max(0,after.sessionInput()-before.sessionInput()),Math.max(0,after.sessionOutput()-before.sessionOutput()),ms,ai.activeModel(false),OffsetDateTime.now());}
 private long[] totals(UUID id){return jdbc.queryForObject("SELECT COALESCE(SUM(input_tokens),0),COALESCE(SUM(output_tokens),0) FROM ai_director_message WHERE review_id=?",(rs,n)->new long[]{rs.getLong(1),rs.getLong(2)},id);}
 private String rolePrompt(Role role,int round,String facts,List<JsonNode> prior)throws Exception{return "你是"+role.name+"。"+role.instruction+"\n这是第"+round+"轮（最多2轮）。事实数据："+facts+"\n已有意见："+mapper.writeValueAsString(prior)+"\n只返回JSON：{summary,findings:[{claim,evidence,risk,recommendation}],eventIds:[],clipIndexes:[],knowledgeRefs:[],confidence:0到1}。不得虚构未确认事实。";}
 private String moderatorPrompt(String facts,List<JsonNode> opinions)throws Exception{return "你是总导演主持人。根据事实和四类评审意见进行分歧与共识检查。事实："+facts+"\n意见："+mapper.writeValueAsString(opinions)+"\n只返回JSON：{summary,consensusScore:0到1,agreements:[],disagreements:[],finalDecisions:[{type,target,action,reason,evidence}],narrativeBeats:[{stage:SETUP或CRISIS或REVERSAL或CLIMAX或RESULT,clipIndex,paceMultiplier:0.5到1.8,musicIntensity:0到1,narrationDirective}],eventIds:[],clipIndexes:[],knowledgeRefs:[],requiresUserConfirmation:true}。narrativeBeats必须覆盖五幕且只能引用事实数据中的镜头；最终决定不得直接应用。";}
 private void reviseDecision(DirectorReviewView view,String modification){try{var confirmed=events.list(view.taskId()).stream().filter(e->"CONFIRMED".equals(e.confirmationStatus())).toList();String prompt="你是总导演。用户要求修改评审结论。已确认事件："+mapper.writeValueAsString(confirmed)+"\n当前结论："+mapper.writeValueAsString(view.finalDecision())+"\n用户要求："+modification+"\n返回与当前结论相同结构的完整JSON，保留五幕narrativeBeats，只能引用已确认事件和现有镜头。";AiUsageService.UsageSnapshot before=usage.snapshot();long began=System.nanoTime();JsonNode revised=ai.chatJson(prompt,List.of(),false,Duration.ofMinutes(4));AiUsageService.UsageSnapshot after=usage.snapshot();Integer seq=jdbc.queryForObject("SELECT COALESCE(MAX(sequence_no),0)+1 FROM ai_director_message WHERE review_id=?",Integer.class,view.id());insertMessage(view.id(),null,view.completedRounds()+1,seq==null?1:seq,"USER_REVISED_DECISION",revised,before,after,(System.nanoTime()-began)/1_000_000);long[] totals=totals(view.id());jdbc.update("UPDATE ai_director_review SET final_decision_json=?,moderator_summary=?,input_tokens=?,output_tokens=?,elapsed_ms=elapsed_ms+? WHERE id=?",mapper.writeValueAsString(revised),revised.path("summary").asText(view.moderatorSummary()),totals[0],totals[1],(System.nanoTime()-began)/1_000_000,view.id());}catch(Exception ex){throw new IllegalStateException("无法按照用户要求修订评审结论："+safe(ex.getMessage()),ex);}}
 private JsonNode read(String value){try{return value==null?mapper.createObjectNode():mapper.readTree(value);}catch(Exception e){return mapper.createObjectNode();}}
 private String safe(String value){if(value==null||value.isBlank())return "未知错误";return value.substring(0,Math.min(900,value.length()));}
 private record Role(String code,String name,String instruction){}
}
