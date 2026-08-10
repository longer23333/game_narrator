package cn.longer233.gamenarrator.director;

import cn.longer233.gamenarrator.ai.AdaptiveAiChatClient;
import cn.longer233.gamenarrator.ai.AiUsageService;
import cn.longer233.gamenarrator.event.*;
import cn.longer233.gamenarrator.identity.CurrentUserContext;
import cn.longer233.gamenarrator.identity.LocalUserContext;
import cn.longer233.gamenarrator.script.ScriptWorkspaceService;
import cn.longer233.gamenarrator.script.StoryboardView;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DirectorReviewServiceTest {
 @Test void oneRoundPersistsFourRolesModeratorEvidenceAndUserDecision(@TempDir Path temp)throws Exception{
  var ds=new DriverManagerDataSource("jdbc:h2:mem:director-review;DB_CLOSE_DELAY=-1","sa","");Flyway.configure().dataSource(ds).load().migrate();var jdbc=new JdbcTemplate(ds);
  UUID task=UUID.randomUUID();jdbc.update("INSERT INTO video_tasks(id,name,game_category,commentary_style,target_duration_seconds,task_brief,source_video_path,status,created_at,owner_id) VALUES(?,?,?,?,?,?,?,?,?,?)",task,"Boss","ACTION","ANIME_THEATER",60,"test","source.mp4","READY",Timestamp.from(Instant.now()),LocalUserContext.LOCAL_USER_ID);
  var events=mock(GameEventTimelineService.class);var workspace=mock(ScriptWorkspaceService.class);var narrative=mock(BattleNarrativePlanService.class);var ai=mock(AdaptiveAiChatClient.class);var current=mock(CurrentUserContext.class);
  when(current.userId()).thenReturn(LocalUserContext.LOCAL_USER_ID);when(ai.activeModel(false)).thenReturn("test-model");
  var event=new GameEventView(UUID.randomUUID(),task,0,10,5,"BOSS_BATTLE",.9,90,"Boss 出现",List.of(),"CONFIRMED",true,"boss-battle-v1",OffsetDateTime.now());when(events.list(task)).thenReturn(List.of(event));when(workspace.storyboard(task)).thenReturn(new StoryboardView("title","synopsis",true,false,List.of()));
  ObjectMapper mapper=new ObjectMapper().registerModule(new JavaTimeModule());
  when(ai.chatJson(anyString(),eq(List.of()),eq(false),any())).thenReturn(mapper.readTree("{\"summary\":\"有证据的建议\",\"eventIds\":[\""+event.id()+"\"],\"clipIndexes\":[],\"knowledgeRefs\":[\"boss-battle-v1\"]}"),mapper.readTree("{\"summary\":\"剧情意见\"}"),mapper.readTree("{\"summary\":\"节奏意见\"}"),mapper.readTree("{\"summary\":\"受众意见\"}"),mapper.readTree("{\"summary\":\"形成共识\",\"consensusScore\":0.8,\"finalDecisions\":[]}"));
  var service=new DirectorReviewService(jdbc,mapper,ai,new AiUsageService(mapper,temp.toString()),current,events,workspace,narrative);
  var review=service.start(task,1);
  assertThat(review.status()).isEqualTo("AWAITING_USER");assertThat(review.messages()).hasSize(5);assertThat(review.consensusScore()).isEqualTo(.8);assertThat(service.decide(review.id(),"ACCEPTED",null).status()).isEqualTo("ACCEPTED");verify(ai,times(5)).chatJson(anyString(),eq(List.of()),eq(false),any());
 }
}
