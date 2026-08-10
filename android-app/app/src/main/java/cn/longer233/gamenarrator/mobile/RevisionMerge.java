package cn.longer233.gamenarrator.mobile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RevisionMerge {
    public enum Mode { UNION, OVERWRITE }

    private RevisionMerge() { }

    public static final class ClipView {
        private final String key;
        private final String name;
        private final long startMs;
        private final long endMs;
        private final boolean muted;
        private final String subtitle;
        private final String narration;
        private final String effectCue;

        ClipView(TimelineClip clip) {
            this.key = clip.key();
            this.name = clip.name();
            this.startMs = clip.startMs();
            this.endMs = clip.endMs();
            this.muted = clip.muted();
            this.subtitle = clip.subtitle();
            this.narration = clip.narration();
            this.effectCue = clip.effectCue();
        }

        public String key() { return key; }
        public String name() { return name; }
        public long startMs() { return startMs; }
        public long endMs() { return endMs; }
        public boolean muted() { return muted; }
        public String subtitle() { return subtitle; }
        public String narration() { return narration; }
        public String effectCue() { return effectCue; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ClipView)) return false;
            ClipView that = (ClipView) other;
            return startMs == that.startMs && endMs == that.endMs && muted == that.muted
                    && Objects.equals(key, that.key) && Objects.equals(name, that.name)
                    && Objects.equals(subtitle, that.subtitle)
                    && Objects.equals(narration, that.narration) && Objects.equals(effectCue, that.effectCue);
        }

        @Override public int hashCode() {
            return Objects.hash(key, name, startMs, endMs, muted, subtitle, narration, effectCue);
        }
    }

    public static final class Diff {
        private final List<ClipView> added;
        private final List<ClipView> removed;
        private final List<ClipView> changed;

        Diff(List<ClipView> added, List<ClipView> removed, List<ClipView> changed) {
            this.added = List.copyOf(added);
            this.removed = List.copyOf(removed);
            this.changed = List.copyOf(changed);
        }

        public List<ClipView> added() { return added; }
        public List<ClipView> removed() { return removed; }
        public List<ClipView> changed() { return changed; }
    }

    public static Diff diff(List<TimelineClip> current, List<TimelineClip> revision) {
        Map<String, ClipView> currentByKey = views(current);
        Map<String, ClipView> revisionByKey = views(revision);
        List<ClipView> added = new ArrayList<>();
        List<ClipView> removed = new ArrayList<>();
        List<ClipView> changed = new ArrayList<>();
        for (Map.Entry<String, ClipView> entry : revisionByKey.entrySet()) {
            ClipView currentView = currentByKey.get(entry.getKey());
            if (currentView == null) added.add(entry.getValue());
            else if (!Objects.equals(currentView, entry.getValue())) changed.add(entry.getValue());
        }
        for (Map.Entry<String, ClipView> entry : currentByKey.entrySet()) {
            if (!revisionByKey.containsKey(entry.getKey())) removed.add(entry.getValue());
        }
        return new Diff(added, removed, changed);
    }

    public static List<TimelineClip> merge(List<TimelineClip> current, List<TimelineClip> revision, Mode mode) {
        List<TimelineClip> result = new ArrayList<>();
        if (current != null) {
            for (TimelineClip clip : current) {
                if (clip != null) result.add(new TimelineClip(clip));
            }
        }
        if (revision == null) return result;
        Map<String, Integer> indexByKey = new HashMap<>();
        for (int i = 0; i < result.size(); i++) indexByKey.put(result.get(i).key(), i);
        for (TimelineClip candidate : revision) {
            if (candidate == null) continue;
            Integer index = indexByKey.get(candidate.key());
            if (index == null) {
                result.add(new TimelineClip(candidate));
                indexByKey.put(candidate.key(), result.size() - 1);
            } else if (mode == Mode.OVERWRITE) {
                result.set(index, new TimelineClip(candidate));
            }
        }
        return result;
    }

    private static Map<String, ClipView> views(List<TimelineClip> clips) {
        Map<String, ClipView> result = new HashMap<>();
        if (clips != null) {
            for (TimelineClip clip : clips) {
                if (clip != null) result.put(clip.key(), new ClipView(clip));
            }
        }
        return result;
    }
}
