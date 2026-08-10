package cn.longer233.gamenarrator.mobile;

import java.util.List;
import java.util.Locale;

public final class MobileEngineReport {
    public static final String INSTALLED = "INSTALLED";
    public static final String MISSING = "MISSING";
    public static final String UNSUPPORTED = "UNSUPPORTED";

    private MobileEngineReport() { }

    public static final class Engine {
        private final String category;
        private final String name;
        private final String state;
        private final String detail;

        public Engine(String category, String name, String state, String detail) {
            this.category = category;
            this.name = name;
            this.state = state;
            this.detail = detail == null ? "" : detail;
        }

        public String category() { return category; }
        public String name() { return name; }
        public String state() { return state; }
        public String detail() { return detail; }
    }

    public static final class Usage {
        private final int projects;
        private final int revisions;
        private final int exports;
        private final int assets;
        private final long databaseBytes;

        public Usage(int projects, int revisions, int exports, int assets, long databaseBytes) {
            this.projects = projects;
            this.revisions = revisions;
            this.exports = exports;
            this.assets = assets;
            this.databaseBytes = databaseBytes;
        }

        public int projects() { return projects; }
        public int revisions() { return revisions; }
        public int exports() { return exports; }
        public int assets() { return assets; }
        public long databaseBytes() { return databaseBytes; }
    }

    public static String build(List<Engine> engines, Usage usage) {
        StringBuilder out = new StringBuilder();
        out.append("端侧引擎（只展示已安装可运行引擎；未安装项明确标注）\n");
        boolean installedShown = false;
        if (engines != null) {
            for (Engine engine : engines) {
                if (engine == null) continue;
                boolean available = INSTALLED.equals(engine.state());
                boolean unsupported = UNSUPPORTED.equals(engine.state());
                if (available) installedShown = true;
                out.append("· ").append(engine.category()).append(" / ").append(engine.name())
                        .append("：").append(unsupported ? "平台不支持" : available ? "已安装" : "未安装");
                if (!engine.detail().isBlank()) out.append(" · ").append(engine.detail());
                out.append('\n');
            }
        }
        if (!installedShown) out.append("（当前没有检测到任何已安装可运行引擎）\n");
        out.append("本地用量：项目 ").append(safeCount(usage == null ? 0 : usage.projects()))
                .append(" 个 · 当前项目版本 ").append(safeCount(usage == null ? 0 : usage.revisions()))
                .append(" 个 · 导出记录 ").append(safeCount(usage == null ? 0 : usage.exports()))
                .append(" 条 · 素材 ").append(safeCount(usage == null ? 0 : usage.assets()))
                .append(" 个 · 数据库 ").append(formatBytes(usage == null ? 0 : usage.databaseBytes())).append('\n');
        return out.toString();
    }

    private static int safeCount(int value) {
        return Math.max(0, value);
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024) return String.format(Locale.CHINA, "%.1f KiB", bytes / 1024d);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.CHINA, "%.1f MiB", bytes / 1024d / 1024);
        return String.format(Locale.CHINA, "%.2f GiB", bytes / 1024d / 1024 / 1024);
    }
}
