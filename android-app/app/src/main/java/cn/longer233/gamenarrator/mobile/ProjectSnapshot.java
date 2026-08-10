package cn.longer233.gamenarrator.mobile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable capture of the editable project state that undo/redo must restore:
 * timeline clips plus all project-scoped subtitle, placement, track, keyframe,
 * audio, visual, review and voice data.
 */
public final class ProjectSnapshot {
    public static final class Placement {
        private final long assetId;
        private final String clipKey;
        private final String role;
        private final float volume;
        private final long fadeInMs;
        private final long fadeOutMs;
        private final float visualX;
        private final float visualY;
        private final float visualScale;

        public Placement(long assetId, String clipKey, String role, float volume, long fadeInMs, long fadeOutMs,
                         float visualX, float visualY, float visualScale) {
            this.assetId = assetId;
            this.clipKey = clipKey;
            this.role = role;
            this.volume = volume;
            this.fadeInMs = fadeInMs;
            this.fadeOutMs = fadeOutMs;
            this.visualX = visualX;
            this.visualY = visualY;
            this.visualScale = visualScale;
        }

        public long assetId() { return assetId; }
        public String clipKey() { return clipKey; }
        public String role() { return role; }
        public float volume() { return volume; }
        public long fadeInMs() { return fadeInMs; }
        public long fadeOutMs() { return fadeOutMs; }
        public float visualX() { return visualX; }
        public float visualY() { return visualY; }
        public float visualScale() { return visualScale; }
    }

    public static final class Track {
        private final String type;
        private final boolean muted;
        private final boolean solo;

        public Track(String type, boolean muted, boolean solo) {
            this.type = type;
            this.muted = muted;
            this.solo = solo;
        }

        public String type() { return type; }
        public boolean muted() { return muted; }
        public boolean solo() { return solo; }
    }

    public static final class Keyframe {
        private final String clipKey;
        private final String property;
        private final long timeMs;
        private final float value;
        private final String easing;

        public Keyframe(String clipKey, String property, long timeMs, float value, String easing) {
            this.clipKey = clipKey;
            this.property = property;
            this.timeMs = timeMs;
            this.value = value;
            this.easing = easing;
        }

        public String clipKey() { return clipKey; }
        public String property() { return property; }
        public long timeMs() { return timeMs; }
        public float value() { return value; }
        public String easing() { return easing; }
    }

    public static final class AudioConfig {
        private final String clipKey;
        private final float volume;
        private final long fadeInMs;
        private final long fadeOutMs;

        public AudioConfig(String clipKey, float volume, long fadeInMs, long fadeOutMs) {
            this.clipKey = clipKey;
            this.volume = volume;
            this.fadeInMs = fadeInMs;
            this.fadeOutMs = fadeOutMs;
        }

        public String clipKey() { return clipKey; }
        public float volume() { return volume; }
        public long fadeInMs() { return fadeInMs; }
        public long fadeOutMs() { return fadeOutMs; }
    }

    public static final class VisualConfig {
        private final String clipKey;
        private final float brightness;
        private final float contrast;
        private final float saturation;
        private final float temperature;
        private final float hue;
        private final float scale;
        private final float rotation;

        public VisualConfig(String clipKey, float brightness, float contrast, float saturation, float temperature,
                            float hue, float scale, float rotation) {
            this.clipKey = clipKey;
            this.brightness = brightness;
            this.contrast = contrast;
            this.saturation = saturation;
            this.temperature = temperature;
            this.hue = hue;
            this.scale = scale;
            this.rotation = rotation;
        }

        public String clipKey() { return clipKey; }
        public float brightness() { return brightness; }
        public float contrast() { return contrast; }
        public float saturation() { return saturation; }
        public float temperature() { return temperature; }
        public float hue() { return hue; }
        public float scale() { return scale; }
        public float rotation() { return rotation; }
    }

    public static final class Review {
        private final String clipKey;
        private final String status;
        private final String note;

        public Review(String clipKey, String status, String note) {
            this.clipKey = clipKey;
            this.status = status;
            this.note = note;
        }

        public String clipKey() { return clipKey; }
        public String status() { return status; }
        public String note() { return note; }
    }

    public static final class Voice {
        private final String clipKey;
        private final String voiceName;
        private final float speed;
        private final float pitch;

        public Voice(String clipKey, String voiceName, float speed, float pitch) {
            this.clipKey = clipKey;
            this.voiceName = voiceName;
            this.speed = speed;
            this.pitch = pitch;
        }

        public String clipKey() { return clipKey; }
        public String voiceName() { return voiceName; }
        public float speed() { return speed; }
        public float pitch() { return pitch; }
    }

    private static final String[] KEYFRAME_PROPERTIES = {"scale", "rotation", "x", "y", "opacity", "volume"};
    private static final String[] TRACK_TYPES = {"ORIGINAL", "NARRATION", "MUSIC"};

    private final List<TimelineClip> clips;
    private final List<SubtitleFileCodec.Cue> subtitles;
    private final List<Placement> placements;
    private final List<Track> tracks;
    private final List<Keyframe> keyframes;
    private final List<AudioConfig> audioConfigs;
    private final List<VisualConfig> visualConfigs;
    private final List<Review> reviews;
    private final List<Voice> voices;

    public ProjectSnapshot(List<TimelineClip> clips, List<SubtitleFileCodec.Cue> subtitles,
                           List<Placement> placements, List<Track> tracks, List<Keyframe> keyframes,
                           List<AudioConfig> audioConfigs, List<VisualConfig> visualConfigs,
                           List<Review> reviews, List<Voice> voices) {
        this.clips = copyClips(clips);
        this.subtitles = subtitles == null ? new ArrayList<>() : new ArrayList<>(subtitles);
        this.placements = placements == null ? new ArrayList<>() : new ArrayList<>(placements);
        this.tracks = tracks == null ? new ArrayList<>() : new ArrayList<>(tracks);
        this.keyframes = keyframes == null ? new ArrayList<>() : new ArrayList<>(keyframes);
        this.audioConfigs = audioConfigs == null ? new ArrayList<>() : new ArrayList<>(audioConfigs);
        this.visualConfigs = visualConfigs == null ? new ArrayList<>() : new ArrayList<>(visualConfigs);
        this.reviews = reviews == null ? new ArrayList<>() : new ArrayList<>(reviews);
        this.voices = voices == null ? new ArrayList<>() : new ArrayList<>(voices);
    }

    public List<TimelineClip> clips() { return copyClips(clips); }
    public List<SubtitleFileCodec.Cue> subtitles() { return new ArrayList<>(subtitles); }
    public List<Placement> placements() { return new ArrayList<>(placements); }
    public List<Track> tracks() { return new ArrayList<>(tracks); }
    public List<Keyframe> keyframes() { return new ArrayList<>(keyframes); }
    public List<AudioConfig> audioConfigs() { return new ArrayList<>(audioConfigs); }
    public List<VisualConfig> visualConfigs() { return new ArrayList<>(visualConfigs); }
    public List<Review> reviews() { return new ArrayList<>(reviews); }
    public List<Voice> voices() { return new ArrayList<>(voices); }

    public static ProjectSnapshot capture(MobileProjectStore store, List<TimelineClip> source) {
        List<TimelineClip> clips = copyClips(source);
        List<SubtitleFileCodec.Cue> subtitles = new ArrayList<>();
        for (ProjectRepository.SubtitleCueInfo cue : store.listSubtitleCues()) {
            subtitles.add(new SubtitleFileCodec.Cue(cue.startMs(), cue.endMs(), cue.text()));
        }
        List<Placement> placements = new ArrayList<>();
        for (MobileAssetStore.PlacementInfo placement : store.listPlacements()) {
            placements.add(new Placement(placement.assetId(), placement.clipKey(), placement.role(), placement.volume(),
                    placement.fadeInMs(), placement.fadeOutMs(), placement.visualX(), placement.visualY(),
                    placement.visualScale()));
        }
        List<Track> tracks = new ArrayList<>();
        for (String type : TRACK_TYPES) {
            ProjectRepository.TrackState state = store.trackState(type);
            tracks.add(new Track(type, state.muted(), state.solo()));
        }
        List<Keyframe> keyframes = new ArrayList<>();
        List<AudioConfig> audioConfigs = new ArrayList<>();
        List<VisualConfig> visualConfigs = new ArrayList<>();
        List<Review> reviews = new ArrayList<>();
        List<Voice> voices = new ArrayList<>();
        for (TimelineClip clip : clips) {
            for (String property : KEYFRAME_PROPERTIES) {
                for (ProjectRepository.KeyframeInfo frame : store.listKeyframes(clip.key(), property)) {
                    keyframes.add(new Keyframe(clip.key(), property, frame.timeMs(), frame.value(), frame.easing()));
                }
            }
            ProjectRepository.ClipAudioConfig audio = store.clipAudioConfig(clip.key());
            audioConfigs.add(new AudioConfig(clip.key(), audio.volume(), audio.fadeInMs(), audio.fadeOutMs()));
            ProjectRepository.ClipVisualConfig visual = store.clipVisualConfig(clip.key());
            visualConfigs.add(new VisualConfig(clip.key(), visual.brightness(), visual.contrast(), visual.saturation(),
                    visual.temperature(), visual.hue(), visual.scale(), visual.rotation()));
            ProjectRepository.ClipReviewInfo review = store.clipReview(clip.key());
            reviews.add(new Review(clip.key(), review.status(), review.note()));
            ProjectRepository.VoiceConfig voice = store.voiceConfig(clip.key());
            voices.add(new Voice(clip.key(), voice.voiceName(), voice.speed(), voice.pitch()));
        }
        return new ProjectSnapshot(clips, subtitles, placements, tracks, keyframes, audioConfigs, visualConfigs,
                reviews, voices);
    }

    public void restore(MobileProjectStore store, List<TimelineClip> target) {
        List<TimelineClip> restored = copyClips(clips);
        target.clear();
        target.addAll(restored);
        store.saveClips(restored, "撤回或恢复编辑");
        store.replaceSubtitleCues(subtitles);
        store.replacePlacements(placements);
        for (Track track : tracks) {
            store.updateTrackState(track.type(), track.muted(), track.solo());
        }
        Map<String, List<Keyframe>> byProperty = new LinkedHashMap<>();
        for (Keyframe keyframe : keyframes) {
            String bucket = keyframe.clipKey() + '\u0000' + keyframe.property();
            byProperty.computeIfAbsent(bucket, ignored -> new ArrayList<>()).add(keyframe);
        }
        for (Map.Entry<String, List<Keyframe>> entry : byProperty.entrySet()) {
            int separator = entry.getKey().indexOf('\u0000');
            String clipKey = entry.getKey().substring(0, separator);
            String property = entry.getKey().substring(separator + 1);
            List<ProjectRepository.KeyframeInfo> frames = new ArrayList<>();
            for (Keyframe keyframe : entry.getValue()) {
                frames.add(new ProjectRepository.KeyframeInfo(keyframe.timeMs(), keyframe.value(), keyframe.easing()));
            }
            store.replaceKeyframes(clipKey, property, frames);
        }
        for (AudioConfig config : audioConfigs) {
            store.saveClipAudioConfig(config.clipKey(), config.volume(), config.fadeInMs(), config.fadeOutMs());
        }
        for (VisualConfig config : visualConfigs) {
            store.saveClipVisualConfig(config.clipKey(), config.brightness(), config.contrast(), config.saturation(),
                    config.temperature(), config.hue(), config.scale(), config.rotation());
        }
        for (Review review : reviews) {
            if ("PENDING".equals(review.status())) {
                store.clearClipReview(review.clipKey());
            } else {
                store.reviewClip(review.clipKey(), review.status(), review.note());
            }
        }
        for (Voice voice : voices) {
            store.saveVoiceConfig(voice.clipKey(), voice.voiceName(), voice.speed(), voice.pitch());
        }
    }

    private static List<TimelineClip> copyClips(List<TimelineClip> source) {
        List<TimelineClip> result = new ArrayList<>();
        if (source != null) {
            for (TimelineClip clip : source) {
                if (clip != null) result.add(new TimelineClip(clip));
            }
        }
        return result;
    }
}
