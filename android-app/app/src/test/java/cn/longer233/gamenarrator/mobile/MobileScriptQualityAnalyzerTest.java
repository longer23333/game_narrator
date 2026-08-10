package cn.longer233.gamenarrator.mobile;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class MobileScriptQualityAnalyzerTest {
    @Test public void completeUniqueSegmentsPass(){MobileScriptQualityAnalyzer.Result result=MobileScriptQualityAnalyzer.analyze(List.of(new MobileScriptQualityAnalyzer.Segment("a",5000,"漂亮反击","主角在最后一刻完成反击","暖色缩放")));assertTrue(result.passed());assertEquals(100,result.score());}
    @Test public void visualConsistencyFlagsMissingObjectButAcceptsMatchedLabel(){
        List<MobileScriptQualityAnalyzer.Issue> missing=MobileScriptQualityAnalyzer.visualConsistency("a","镜头里有一只猫跑过",List.of("sports car (99%)"));
        assertFalse(missing.isEmpty());
        assertTrue(missing.get(0).message().contains("猫"));
        List<MobileScriptQualityAnalyzer.Issue> matched=MobileScriptQualityAnalyzer.visualConsistency("a","镜头里有一只猫跑过",List.of("tabby cat (96%)","person (90%)"));
        assertTrue(matched.isEmpty());
        List<MobileScriptQualityAnalyzer.Issue> blank=MobileScriptQualityAnalyzer.visualConsistency("a","",List.of("tabby cat (96%)"));
        assertTrue(blank.isEmpty());
    }
    @Test public void detectsBlanksOverflowDuplicatesAndMojibake(){MobileScriptQualityAnalyzer.Result result=MobileScriptQualityAnalyzer.analyze(List.of(new MobileScriptQualityAnalyzer.Segment("a",1000,"重复字幕","这是一段明显过长而无法在一秒内读完的解说文案",""),new MobileScriptQualityAnalyzer.Segment("b",5000,"重复字幕","�", "冷色")));assertFalse(result.passed());assertTrue(result.issues().stream().anyMatch(x->x.message().contains("配音时长")));assertTrue(result.issues().stream().anyMatch(x->x.message().contains("重复")));assertTrue(result.issues().stream().anyMatch(x->x.message().contains("乱码")));}
}
