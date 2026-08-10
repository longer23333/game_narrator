package cn.longer233.gamenarrator.mobile;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON codec for {@link ProjectSnapshot}, used by the persistent edit history.
 */
public final class ProjectSnapshotJson {
    private ProjectSnapshotJson() { }

    public static String toJson(ProjectSnapshot snapshot) throws JSONException {
        JSONObject root = new JSONObject();
        JSONArray clips = new JSONArray();
        for (TimelineClip clip : snapshot.clips()) {
            JSONObject value = new JSONObject();
            value.put("key", clip.key());
            value.put("uri", clip.uri() == null ? "" : clip.uri().toString());
            value.put("name", clip.name());
            value.put("startMs", clip.startMs());
            value.put("endMs", clip.endMs());
            value.put("muted", clip.muted());
            value.put("track", clip.track());
            value.put("subtitle", clip.subtitle());
            value.put("narration", clip.narration());
            value.put("effectCue", clip.effectCue());
            clips.put(value);
        }
        root.put("clips", clips);
        JSONArray subtitles = new JSONArray();
        for (SubtitleFileCodec.Cue cue : snapshot.subtitles()) {
            JSONObject value = new JSONObject();
            value.put("startMs", cue.startMs());
            value.put("endMs", cue.endMs());
            value.put("text", cue.text());
            subtitles.put(value);
        }
        root.put("subtitles", subtitles);
        JSONArray placements = new JSONArray();
        for (ProjectSnapshot.Placement placement : snapshot.placements()) {
            JSONObject value = new JSONObject();
            value.put("assetId", placement.assetId());
            value.put("clipKey", placement.clipKey());
            value.put("role", placement.role());
            value.put("volume", placement.volume());
            value.put("fadeInMs", placement.fadeInMs());
            value.put("fadeOutMs", placement.fadeOutMs());
            value.put("visualX", placement.visualX());
            value.put("visualY", placement.visualY());
            value.put("visualScale", placement.visualScale());
            placements.put(value);
        }
        root.put("placements", placements);
        JSONArray tracks = new JSONArray();
        for (ProjectSnapshot.Track track : snapshot.tracks()) {
            JSONObject value = new JSONObject();
            value.put("type", track.type());
            value.put("muted", track.muted());
            value.put("solo", track.solo());
            tracks.put(value);
        }
        root.put("tracks", tracks);
        JSONArray keyframes = new JSONArray();
        for (ProjectSnapshot.Keyframe frame : snapshot.keyframes()) {
            JSONObject value = new JSONObject();
            value.put("clipKey", frame.clipKey());
            value.put("property", frame.property());
            value.put("timeMs", frame.timeMs());
            value.put("value", frame.value());
            value.put("easing", frame.easing());
            keyframes.put(value);
        }
        root.put("keyframes", keyframes);
        JSONArray audioConfigs = new JSONArray();
        for (ProjectSnapshot.AudioConfig config : snapshot.audioConfigs()) {
            JSONObject value = new JSONObject();
            value.put("clipKey", config.clipKey());
            value.put("volume", config.volume());
            value.put("fadeInMs", config.fadeInMs());
            value.put("fadeOutMs", config.fadeOutMs());
            audioConfigs.put(value);
        }
        root.put("audioConfigs", audioConfigs);
        JSONArray visualConfigs = new JSONArray();
        for (ProjectSnapshot.VisualConfig config : snapshot.visualConfigs()) {
            JSONObject value = new JSONObject();
            value.put("clipKey", config.clipKey());
            value.put("brightness", config.brightness());
            value.put("contrast", config.contrast());
            value.put("saturation", config.saturation());
            value.put("temperature", config.temperature());
            value.put("hue", config.hue());
            value.put("scale", config.scale());
            value.put("rotation", config.rotation());
            visualConfigs.put(value);
        }
        root.put("visualConfigs", visualConfigs);
        JSONArray reviews = new JSONArray();
        for (ProjectSnapshot.Review review : snapshot.reviews()) {
            JSONObject value = new JSONObject();
            value.put("clipKey", review.clipKey());
            value.put("status", review.status());
            value.put("note", review.note());
            reviews.put(value);
        }
        root.put("reviews", reviews);
        JSONArray voices = new JSONArray();
        for (ProjectSnapshot.Voice voice : snapshot.voices()) {
            JSONObject value = new JSONObject();
            value.put("clipKey", voice.clipKey());
            value.put("voiceName", voice.voiceName());
            value.put("speed", voice.speed());
            value.put("pitch", voice.pitch());
            voices.put(value);
        }
        root.put("voices", voices);
        return root.toString();
    }

    public static ProjectSnapshot fromJson(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        List<TimelineClip> clips = new ArrayList<>();
        JSONArray clipArray = root.optJSONArray("clips");
        if (clipArray != null) {
            for (int i = 0; i < clipArray.length(); i++) {
                JSONObject value = clipArray.getJSONObject(i);
                TimelineClip clip = new TimelineClip(value.optString("key", ""),
                        Uri.parse(value.optString("uri", "")), value.optString("name", ""),
                        value.optLong("startMs", 0), value.optLong("endMs", 0));
                clip.setTrack(value.optString("track", "V1"));
                clip.update(value.optLong("startMs", 0), value.optLong("endMs", 0),
                        value.optBoolean("muted"), value.optString("subtitle", ""));
                clip.updateCreativeText(value.optString("subtitle", ""),
                        value.optString("narration", ""), value.optString("effectCue", ""));
                clips.add(clip);
            }
        }
        List<SubtitleFileCodec.Cue> subtitles = new ArrayList<>();
        JSONArray subtitleArray = root.optJSONArray("subtitles");
        if (subtitleArray != null) {
            for (int i = 0; i < subtitleArray.length(); i++) {
                JSONObject value = subtitleArray.getJSONObject(i);
                subtitles.add(new SubtitleFileCodec.Cue(value.optLong("startMs", 0),
                        value.optLong("endMs", 0), value.optString("text", "")));
            }
        }
        List<ProjectSnapshot.Placement> placements = new ArrayList<>();
        JSONArray placementArray = root.optJSONArray("placements");
        if (placementArray != null) {
            for (int i = 0; i < placementArray.length(); i++) {
                JSONObject value = placementArray.getJSONObject(i);
                placements.add(new ProjectSnapshot.Placement(value.optLong("assetId", 0),
                        value.optString("clipKey", ""), value.optString("role", ""),
                        (float) value.optDouble("volume", 1), value.optLong("fadeInMs", 0),
                        value.optLong("fadeOutMs", 0), (float) value.optDouble("visualX", .68),
                        (float) value.optDouble("visualY", .66), (float) value.optDouble("visualScale", .31)));
            }
        }
        List<ProjectSnapshot.Track> tracks = new ArrayList<>();
        JSONArray trackArray = root.optJSONArray("tracks");
        if (trackArray != null) {
            for (int i = 0; i < trackArray.length(); i++) {
                JSONObject value = trackArray.getJSONObject(i);
                tracks.add(new ProjectSnapshot.Track(value.optString("type", ""),
                        value.optBoolean("muted"), value.optBoolean("solo")));
            }
        }
        List<ProjectSnapshot.Keyframe> keyframes = new ArrayList<>();
        JSONArray keyframeArray = root.optJSONArray("keyframes");
        if (keyframeArray != null) {
            for (int i = 0; i < keyframeArray.length(); i++) {
                JSONObject value = keyframeArray.getJSONObject(i);
                keyframes.add(new ProjectSnapshot.Keyframe(value.optString("clipKey", ""),
                        value.optString("property", ""), value.optLong("timeMs", 0),
                        (float) value.optDouble("value", 0), value.optString("easing", "LINEAR")));
            }
        }
        List<ProjectSnapshot.AudioConfig> audioConfigs = new ArrayList<>();
        JSONArray audioArray = root.optJSONArray("audioConfigs");
        if (audioArray != null) {
            for (int i = 0; i < audioArray.length(); i++) {
                JSONObject value = audioArray.getJSONObject(i);
                audioConfigs.add(new ProjectSnapshot.AudioConfig(value.optString("clipKey", ""),
                        (float) value.optDouble("volume", 1), value.optLong("fadeInMs", 0),
                        value.optLong("fadeOutMs", 0)));
            }
        }
        List<ProjectSnapshot.VisualConfig> visualConfigs = new ArrayList<>();
        JSONArray visualArray = root.optJSONArray("visualConfigs");
        if (visualArray != null) {
            for (int i = 0; i < visualArray.length(); i++) {
                JSONObject value = visualArray.getJSONObject(i);
                visualConfigs.add(new ProjectSnapshot.VisualConfig(value.optString("clipKey", ""),
                        (float) value.optDouble("brightness", 0), (float) value.optDouble("contrast", 0),
                        (float) value.optDouble("saturation", 0), (float) value.optDouble("temperature", 0),
                        (float) value.optDouble("hue", 0), (float) value.optDouble("scale", 1),
                        (float) value.optDouble("rotation", 0)));
            }
        }
        List<ProjectSnapshot.Review> reviews = new ArrayList<>();
        JSONArray reviewArray = root.optJSONArray("reviews");
        if (reviewArray != null) {
            for (int i = 0; i < reviewArray.length(); i++) {
                JSONObject value = reviewArray.getJSONObject(i);
                reviews.add(new ProjectSnapshot.Review(value.optString("clipKey", ""),
                        value.optString("status", "PENDING"), value.optString("note", "")));
            }
        }
        List<ProjectSnapshot.Voice> voices = new ArrayList<>();
        JSONArray voiceArray = root.optJSONArray("voices");
        if (voiceArray != null) {
            for (int i = 0; i < voiceArray.length(); i++) {
                JSONObject value = voiceArray.getJSONObject(i);
                voices.add(new ProjectSnapshot.Voice(value.optString("clipKey", ""),
                        value.optString("voiceName", ""), (float) value.optDouble("speed", 1),
                        (float) value.optDouble("pitch", 1)));
            }
        }
        return new ProjectSnapshot(clips, subtitles, placements, tracks, keyframes, audioConfigs,
                visualConfigs, reviews, voices);
    }
}
