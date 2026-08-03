package cn.longer233.gamenarrator.media;

import java.util.List;

public record MediaPreparationResult(
        String extractedAudioPath,
        String sceneManifestPath,
        List<SceneFrame> scenes
) {
}
