package cn.longer233.gamenarrator.mobile;

public final class EditSnapshot {
    private final long startMs;
    private final long endMs;
    private final boolean muted;
    private final String subtitle;
    public EditSnapshot(long startMs, long endMs, boolean muted, String subtitle) {
        this.startMs = startMs; this.endMs = endMs; this.muted = muted; this.subtitle = subtitle;
    }
    public long startMs() { return startMs; }
    public long endMs() { return endMs; }
    public boolean muted() { return muted; }
    public String subtitle() { return subtitle; }
}
