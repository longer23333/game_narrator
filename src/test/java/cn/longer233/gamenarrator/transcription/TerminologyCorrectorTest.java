package cn.longer233.gamenarrator.transcription;

import cn.longer233.gamenarrator.event.BossBattleKnowledgePack;
import cn.longer233.gamenarrator.event.GameKnowledgePackService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TerminologyCorrectorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mergesKnowledgeTermsAndUserOverridesWhilePreservingAllTimestamps(@TempDir Path directory) throws Exception {
        GameKnowledgePackService packs = mock(GameKnowledgePackService.class);
        when(packs.activeTerminology()).thenReturn(List.of(
                new BossBattleKnowledgePack.TerminologyEntry("弹反", List.of("谈反", "弹返")),
                new BossBattleKnowledgePack.TerminologyEntry("Boss", List.of("包斯"))));
        TerminologyCorrector corrector = new TerminologyCorrector(packs, mapper);

        Path text = directory.resolve("transcript.txt");
        Path subtitle = directory.resolve("transcript.srt");
        Path detail = directory.resolve("transcript.json");
        Files.writeString(text, "谈反包斯", StandardCharsets.UTF_8);
        String timelineOne = "00:00:01,250 --> 00:00:03,875";
        String timelineTwo = "00:00:04,125 --> 00:00:06,500 position:50%";
        Files.writeString(subtitle, "1\r\n" + timelineOne + "\r\n谈反包斯\r\n\r\n2\r\n"
                + timelineTwo + "\r\n弹返成功\r\n", StandardCharsets.UTF_8);
        Files.writeString(detail, """
                {"model":"包斯-v1","transcription":[
                  {"timestamps":{"from":"00:00:01,250","to":"00:00:03,875"},"offsets":{"from":1250,"to":3875},"text":"谈反包斯"},
                  {"start":4.125,"end":6.5,"sentence":"弹返成功"}
                ]}
                """, StandardCharsets.UTF_8);

        TranscriptionResult result = corrector.correct(new TranscriptionResult("谈反包斯", text.toString(),
                subtitle.toString(), detail.toString()), "谈反=完美弹反");

        assertThat(result.text()).isEqualTo("完美弹反Boss");
        assertThat(Files.readString(text)).isEqualTo("完美弹反Boss");
        String correctedSrt = Files.readString(subtitle);
        assertThat(correctedSrt).contains(timelineOne, timelineTwo, "完美弹反Boss", "弹反成功");
        var correctedJson = mapper.readTree(detail.toFile());
        assertThat(correctedJson.path("model").asText()).isEqualTo("包斯-v1");
        assertThat(correctedJson.at("/transcription/0/timestamps/from").asText()).isEqualTo("00:00:01,250");
        assertThat(correctedJson.at("/transcription/0/timestamps/to").asText()).isEqualTo("00:00:03,875");
        assertThat(correctedJson.at("/transcription/0/offsets/from").asLong()).isEqualTo(1250);
        assertThat(correctedJson.at("/transcription/0/offsets/to").asLong()).isEqualTo(3875);
        assertThat(correctedJson.at("/transcription/1/start").asDouble()).isEqualTo(4.125);
        assertThat(correctedJson.at("/transcription/1/end").asDouble()).isEqualTo(6.5);
        assertThat(correctedJson.at("/transcription/0/text").asText()).isEqualTo("完美弹反Boss");
        assertThat(correctedJson.at("/transcription/1/sentence").asText()).isEqualTo("弹反成功");
    }

    @Test
    void appliesLongerAliasesBeforeShorterAliases() {
        GameKnowledgePackService packs = mock(GameKnowledgePackService.class);
        when(packs.activeTerminology()).thenReturn(List.of(
                new BossBattleKnowledgePack.TerminologyEntry("纳什男爵", List.of("大龙")),
                new BossBattleKnowledgePack.TerminologyEntry("龙", List.of("龙"))));
        TerminologyCorrector corrector = new TerminologyCorrector(packs, mapper);

        var result = corrector.correct(new TranscriptionResult("大龙刷新", null, null, null), "大=巨大");

        assertThat(result.text()).isEqualTo("纳什男爵刷新");
    }
}
