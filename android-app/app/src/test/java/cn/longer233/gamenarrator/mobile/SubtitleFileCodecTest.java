package cn.longer233.gamenarrator.mobile;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class SubtitleFileCodecTest {
    @Test public void parsesBomIndexesAndMultilineText(){List<SubtitleFileCodec.Cue> cues=SubtitleFileCodec.parse("\uFEFF1\r\n00:00:01,250 --> 00:00:03,500\r\n第一行\r\n第二行\r\n\r\n2\r\n00:01:00.000 --> 00:01:01.100\r\n结束");assertEquals(2,cues.size());assertEquals(1250,cues.get(0).startMs());assertEquals("第一行\n第二行",cues.get(0).text());assertEquals(61100,cues.get(1).endMs());}
    @Test public void skipsInvalidBlocksAndRoundTrips(){List<SubtitleFileCodec.Cue> input=List.of(new SubtitleFileCodec.Cue(0,999,"开场"),new SubtitleFileCodec.Cue(3_600_001,3_602_345,"两行\n字幕"));String encoded=SubtitleFileCodec.format(input);List<SubtitleFileCodec.Cue> output=SubtitleFileCodec.parse("bad\n\n"+encoded);assertEquals(2,output.size());assertEquals(input.get(1).startMs(),output.get(1).startMs());assertEquals(input.get(1).text(),output.get(1).text());}
}
