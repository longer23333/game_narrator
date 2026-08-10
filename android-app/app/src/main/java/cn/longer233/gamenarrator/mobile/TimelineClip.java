package cn.longer233.gamenarrator.mobile;

import android.net.Uri;

public final class TimelineClip {
    private final String key;
    private final Uri uri;
    private final String name;
    private long startMs;
    private long endMs;
    private boolean muted;
    private String track;
    private String subtitle;
    private String narration;
    private String effectCue;

    public TimelineClip(Uri uri, String name, long startMs, long endMs) {
        this(java.util.UUID.randomUUID().toString(), uri, name, startMs, endMs);
    }
    public TimelineClip(String key, Uri uri, String name, long startMs, long endMs) {
        this.key = key == null || key.isBlank() ? java.util.UUID.randomUUID().toString() : key;
        this.uri = uri;
        this.name = name;
        this.startMs = startMs;
        this.endMs = endMs;
        this.track = "V1";
        this.subtitle = "";
        this.narration = "";
        this.effectCue = "";
    }

    public TimelineClip(TimelineClip source) {
        this(source.key, source.uri, source.name, source.startMs, source.endMs);
        this.muted = source.muted;
        this.track = source.track;
        this.subtitle = source.subtitle;
        this.narration = source.narration;
        this.effectCue = source.effectCue;
    }

    public String key() { return key; }
    public Uri uri() { return uri; }
    public String name() { return name; }
    public long startMs() { return startMs; }
    public long endMs() { return endMs; }
    public boolean muted() { return muted; }
    public String track() { return track; }
    public String subtitle() { return subtitle; }
    public String narration() { return narration; }
    public String effectCue() { return effectCue; }
    public long durationMs() { return Math.max(0, endMs - startMs); }
    public void update(long startMs, long endMs, boolean muted, String subtitle) {
        this.startMs = startMs;
        this.endMs = endMs;
        this.muted = muted;
        this.subtitle = subtitle == null ? "" : subtitle;
    }
    public void setTrack(String track) {
        this.track = (track == null || track.isBlank()) ? "V1" : track;
    }
    public void updateCreativeText(String subtitle, String narration, String effectCue) {
        this.subtitle = subtitle == null ? "" : subtitle;
        this.narration = narration == null ? "" : narration;
        this.effectCue = effectCue == null ? "" : effectCue;
    }
}
