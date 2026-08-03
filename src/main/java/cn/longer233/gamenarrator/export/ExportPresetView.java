package cn.longer233.gamenarrator.export;

import java.util.UUID;

public record ExportPresetView(
        UUID id,
        String name,
        String description,
        String container,
        String videoCodec,
        String audioCodec,
        Integer width,
        Integer height,
        Double frameRate,
        String rateControl,
        Integer qualityValue,
        Integer targetBitrateKbps,
        String hardwareEncoder,
        Integer audioBitrateKbps,
        Integer audioSampleRate,
        String subtitleMode
) {
}
