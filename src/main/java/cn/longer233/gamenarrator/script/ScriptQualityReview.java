package cn.longer233.gamenarrator.script;

import java.util.List;

public record ScriptQualityReview(int score, boolean passed, List<String> issues, String summary) {
    public static ScriptQualityReview unavailable() {
        return new ScriptQualityReview(0, false, List.of(), "尚未生成质量评审");
    }
}
