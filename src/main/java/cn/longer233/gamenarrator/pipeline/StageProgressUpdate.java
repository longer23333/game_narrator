package cn.longer233.gamenarrator.pipeline;

public record StageProgressUpdate(int percent, String unit, int current, int total, String detail) {
    public StageProgressUpdate {
        percent = Math.max(10, Math.min(99, percent));
        unit = unit == null ? "" : unit.strip().toUpperCase(java.util.Locale.ROOT);
        current = Math.max(0, current);
        total = Math.max(current, total);
        detail = detail == null ? "" : detail.strip();
        if (unit.length() > 24) unit = unit.substring(0, 24);
        if (detail.length() > 240) detail = detail.substring(0, 240);
    }

    public static StageProgressUpdate of(int percent, String unit, int current, int total, String detail) {
        return new StageProgressUpdate(percent, unit, current, total, detail);
    }
}
