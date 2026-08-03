package cn.longer233.gamenarrator.export;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateExportRequest(
        @NotNull UUID presetId,
        @NotBlank String exportName,
        Integer width,
        Integer height,
        Double frameRate,
        Integer qualityValue,
        Integer targetBitrateKbps,
        String subtitleMode
) {
}
