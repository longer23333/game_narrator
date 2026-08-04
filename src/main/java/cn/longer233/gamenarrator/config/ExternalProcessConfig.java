package cn.longer233.gamenarrator.config;

import cn.longer233.gamenarrator.common.ExternalProcessRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExternalProcessConfig {
    public ExternalProcessConfig(
            @Value("${game-narrator.external-process.ffmpeg-max-concurrent:1}") int ffmpeg,
            @Value("${game-narrator.external-process.whisper-max-concurrent:1}") int whisper,
            @Value("${game-narrator.external-process.other-max-concurrent:2}") int other) {
        ExternalProcessRunner.configureLimits(ffmpeg, whisper, other);
    }
}
