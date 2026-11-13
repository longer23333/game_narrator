package cn.longer233.gamenarrator.director;

import cn.longer233.gamenarrator.ai.AiSettingsService;
import cn.longer233.gamenarrator.ai.AiUsageService;
import cn.longer233.gamenarrator.ai.OllamaChatBackend;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "gamenarrator.localModelSmoke", matches = "true")
class DirectorReviewLocalModelTest {
    @Test
    void localModelCompletesFourOpinionsAndModerator(@TempDir Path temp) throws Exception {
        var mapper = new ObjectMapper();
        var backend = new OllamaChatBackend(mapper, new AiUsageService(mapper, temp.toString()), "http://127.0.0.1:11434");
        var settings = new AiSettingsService.Settings("LOCAL", "DASHSCOPE", "", "",
                "qwen2.5vl:3b", "qwen2.5vl:3b", 0d, 0d, 0d);
        String facts = "已确认事实：事件11111111-1111-1111-1111-111111111111，镜头1，Boss进入战场；镜头2，玩家击败Boss。";
        var opinions = new ArrayList<JsonNode>();
        for (String role : List.of("事实", "剧情", "节奏", "受众")) {
            var schema = DirectorReviewSchemas.opinion();
            var result = backend.chatJson("你是" + role + "评审。" + facts + "请简洁给出意见，严格遵守JSON Schema：" + schema,
                    List.of(), "qwen2.5vl:3b", settings, Duration.ofMinutes(2), schema);
            assertThat(result.path("summary").asText()).isNotBlank();
            assertThat(result.path("findings").size()).isLessThanOrEqualTo(3);
            opinions.add(result);
        }
        var schema = DirectorReviewSchemas.decision();
        var decision = backend.chatJson("你是总导演。" + facts + "四个角色意见：" + mapper.writeValueAsString(opinions)
                        + "给出简洁共识和五幕计划，每个stage只能出现一次，只引用镜头1或2。严格遵守JSON Schema：" + schema,
                List.of(), "qwen2.5vl:3b", settings, Duration.ofMinutes(2), schema);
        assertThat(decision.path("narrativeBeats").size()).isEqualTo(5);
        assertThat(decision.path("requiresUserConfirmation").asBoolean()).isTrue();
    }
}
