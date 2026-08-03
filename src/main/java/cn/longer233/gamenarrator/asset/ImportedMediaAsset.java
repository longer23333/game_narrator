package cn.longer233.gamenarrator.asset;

import java.util.List;

public record ImportedMediaAsset(
        String sourceUrl,
        String title,
        String creator,
        String previewUrl,
        Double durationSeconds,
        List<String> platformTags
) {
}
