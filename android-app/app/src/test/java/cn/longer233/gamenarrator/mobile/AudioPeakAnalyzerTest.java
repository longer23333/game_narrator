package cn.longer233.gamenarrator.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class AudioPeakAnalyzerTest {
    @Test public void detectsSustainedOverloadAndSuggestsSafeGain(){AudioPeakAnalyzer.Result result=AudioPeakAnalyzer.analyze(new float[]{.2f,.99f,1f,.995f,.4f});assertTrue(result.overloaded());assertEquals(.9f,result.suggestedGain(),.001f);assertEquals(3,result.overloadBuckets());}
    @Test public void detectsMostlySilentSignal(){AudioPeakAnalyzer.Result result=AudioPeakAnalyzer.analyze(new float[]{0,.01f,.02f,.03f,.04f,.05f,.06f,.5f});assertTrue(result.mostlySilent());assertFalse(result.overloaded());}
}
