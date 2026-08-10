package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class MobileEngineReportTest {
    @Test public void onlyInstalledEnginesAreShownAsAvailable() {
        String report = MobileEngineReport.build(Arrays.asList(
                new MobileEngineReport.Engine("语音合成", "Android 系统 TTS", MobileEngineReport.INSTALLED, "2 个离线音色"),
                new MobileEngineReport.Engine("转写", "端侧转写引擎", MobileEngineReport.MISSING, "")),
                new MobileEngineReport.Usage(2, 3, 4, 5, 1024));
        assertTrue(report.contains("Android 系统 TTS：已安装"));
        assertTrue(report.contains("2 个离线音色"));
        assertTrue(report.contains("端侧转写引擎：未安装"));
        assertFalse(report.contains("转写引擎：已安装"));
        assertTrue(report.contains("项目 2 个"));
        assertTrue(report.contains("数据库 1.0 KiB"));
    }

    @Test public void noInstalledEnginesProducesClearState() {
        String report = MobileEngineReport.build(Arrays.asList(
                new MobileEngineReport.Engine("转写", "端侧转写引擎", MobileEngineReport.MISSING, "")),
                null);
        assertTrue(report.contains("没有检测到任何已安装可运行引擎"));
        assertTrue(report.contains("项目 0 个"));
    }
}
