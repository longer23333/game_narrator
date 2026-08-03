package cn.longer233.gamenarrator.export;

import java.util.OptionalInt;

final class FfmpegProgressParser {
    private final double durationSeconds;

    FfmpegProgressParser(double durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    OptionalInt parsePercent(String line) {
        if (line == null || durationSeconds <= 0) return OptionalInt.empty();
        int separator = line.indexOf('=');
        if (separator <= 0) return OptionalInt.empty();
        String key = line.substring(0, separator).trim();
        String value = line.substring(separator + 1).trim();
        double elapsedSeconds;
        try {
            if ("out_time_us".equals(key) || "out_time_ms".equals(key)) {
                elapsedSeconds = Long.parseLong(value) / 1_000_000d;
            } else if ("out_time".equals(key)) {
                elapsedSeconds = parseClock(value);
            } else {
                return OptionalInt.empty();
            }
        } catch (NumberFormatException exception) {
            return OptionalInt.empty();
        }
        int percent = 10 + (int) Math.floor(Math.max(0, elapsedSeconds) / durationSeconds * 80);
        return OptionalInt.of(Math.max(10, Math.min(89, percent)));
    }

    private double parseClock(String value) {
        String[] parts = value.split(":");
        if (parts.length != 3) throw new NumberFormatException("invalid FFmpeg clock");
        return Long.parseLong(parts[0]) * 3600d + Long.parseLong(parts[1]) * 60d
                + Double.parseDouble(parts[2]);
    }
}
