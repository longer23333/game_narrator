package cn.longer233.gamenarrator.personalization;

import java.util.List;

public record DirectorProfileView(
        int decisionCount,
        double preferredDurationRatio,
        double preferredTextDensityRatio,
        List<String> preferredEffects,
        String summary
) {
}
