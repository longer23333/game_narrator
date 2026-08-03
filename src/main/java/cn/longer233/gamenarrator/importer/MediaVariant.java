package cn.longer233.gamenarrator.importer;

public record MediaVariant(
        String formatId,
        String label,
        String extension,
        Integer width,
        Integer height,
        Double frameRate,
        String videoCodec,
        String audioCodec,
        Long approximateBytes
) {
}
