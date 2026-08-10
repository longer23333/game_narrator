package cn.longer233.gamenarrator.mobile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure conversion between project domain data and the .gnproject.json archive
 * format. Database access stays in the stores; this class only maps values.
 */
public final class ProjectSerializer {
    public static final String FORMAT = "GameNarratorAndroidProject";
    public static final int SCHEMA_VERSION = 1;

    public static final class RevisionArchive {
        private final long createdAt;
        private final String reason;
        private final List<TimelineClip> clips;

        public RevisionArchive(long createdAt, String reason, List<TimelineClip> clips) {
            this.createdAt = createdAt;
            this.reason = reason == null ? "" : reason;
            this.clips = clips == null ? new ArrayList<>() : new ArrayList<>(clips);
        }

        public long createdAt() { return createdAt; }
        public String reason() { return reason; }
        public List<TimelineClip> clips() { return new ArrayList<>(clips); }
    }

    public static final class ClipRow {
        private final int position;
        private final String key, uri, name, subtitle, narration, effectCue, track;
        private final long startMs, endMs;
        private final boolean muted;

        ClipRow(int position, String key, String uri, String name, long startMs, long endMs, boolean muted,
                String subtitle, String narration, String effectCue, String track) {
            this.position = position;
            this.key = key;
            this.uri = uri;
            this.name = name;
            this.startMs = startMs;
            this.endMs = endMs;
            this.muted = muted;
            this.subtitle = subtitle;
            this.narration = narration;
            this.effectCue = effectCue;
            this.track = track;
        }

        public int position() { return position; }
        public String key() { return key; }
        public String uri() { return uri; }
        public String name() { return name; }
        public long startMs() { return startMs; }
        public long endMs() { return endMs; }
        public boolean muted() { return muted; }
        public String subtitle() { return subtitle; }
        public String narration() { return narration; }
        public String effectCue() { return effectCue; }
        public String track() { return track; }
    }

    public static final class TemplateDraft {
        private final String name, cue;

        public TemplateDraft(String name, String cue) {
            this.name = name == null ? "" : name;
            this.cue = cue == null ? "" : cue;
        }

        public String name() { return name; }
        public String cue() { return cue; }
    }

    private ProjectSerializer() { }

    public static JSONObject projectJson(String name, List<TimelineClip> timeline,
                                         List<ProjectRepository.SubtitleCueInfo> subtitles,
                                         List<ProjectRepository.ClipReviewInfo> reviews,
                                         List<RevisionArchive> revisions,
                                         Map<String, ProjectRepository.TrackState> tracks) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("exportedAt", System.currentTimeMillis());
        root.put("name", name);
        root.put("timeline", timelineJson(timeline));
        JSONArray subtitleCues = new JSONArray();
        if (subtitles != null) {
            for (ProjectRepository.SubtitleCueInfo cue : subtitles) {
                JSONObject value = new JSONObject();
                value.put("startMs", cue.startMs());
                value.put("endMs", cue.endMs());
                value.put("text", cue.text());
                subtitleCues.put(value);
            }
        }
        root.put("subtitleCues", subtitleCues);
        JSONArray clipReviews = new JSONArray();
        if (reviews != null) {
            for (ProjectRepository.ClipReviewInfo review : reviews) {
                JSONObject value = new JSONObject();
                value.put("clipKey", review.clipKey());
                value.put("status", review.status());
                value.put("note", review.note());
                value.put("updatedAt", review.updatedAt());
                clipReviews.put(value);
            }
        }
        root.put("clipReviews", clipReviews);
        JSONArray revisionArray = new JSONArray();
        if (revisions != null) {
            for (RevisionArchive revision : revisions) {
                JSONObject value = new JSONObject();
                value.put("createdAt", revision.createdAt());
                value.put("reason", revision.reason());
                value.put("clips", timelineJson(revision.clips()));
                revisionArray.put(value);
            }
        }
        root.put("revisions", revisionArray);
        JSONObject trackStates = new JSONObject();
        if (tracks != null) {
            for (Map.Entry<String, ProjectRepository.TrackState> entry : tracks.entrySet()) {
                JSONObject value = new JSONObject();
                value.put("muted", entry.getValue().muted());
                value.put("solo", entry.getValue().solo());
                trackStates.put(entry.getKey(), value);
            }
        }
        root.put("trackStates", trackStates);
        return root;
    }

    public static JSONArray timelineJson(List<TimelineClip> clips) throws JSONException {
        JSONArray out = new JSONArray();
        if (clips != null) {
            for (int i = 0; i < clips.size(); i++) out.put(clipJson(clips.get(i), i));
        }
        return out;
    }

    public static JSONObject clipJson(TimelineClip clip, int position) throws JSONException {
        JSONObject value = new JSONObject();
        value.put("position", position);
        value.put("key", clip.key());
        value.put("uri", clip.uri().toString());
        value.put("name", clip.name());
        value.put("startMs", clip.startMs());
        value.put("endMs", clip.endMs());
        value.put("muted", clip.muted());
        value.put("subtitle", clip.subtitle());
        value.put("narration", clip.narration());
        value.put("effectCue", clip.effectCue());
        value.put("track", clip.track());
        return value;
    }

    public static ClipRow clipRow(JSONObject value, int position) throws JSONException {
        long start = value.optLong("startMs", 0), end = value.optLong("endMs", 0);
        if (end <= start) throw new IllegalArgumentException("项目归档包含无效片段时长");
        return new ClipRow(position, value.optString("key", java.util.UUID.randomUUID().toString()),
                value.getString("uri"), value.optString("name", "恢复片段"), start, end,
                value.optBoolean("muted"), value.optString("subtitle", ""), value.optString("narration", ""),
                value.optString("effectCue", ""), value.optString("track", "V1"));
    }

    public static JSONObject parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (!FORMAT.equals(root.optString("format"))) {
            throw new IllegalArgumentException("不是 GameNarrator Android 项目归档");
        }
        if (root.optInt("schemaVersion", 0) != SCHEMA_VERSION) {
            throw new IllegalArgumentException("不支持的项目归档版本");
        }
        JSONArray timeline = root.optJSONArray("timeline");
        if (timeline == null || timeline.length() > 10000) {
            throw new IllegalArgumentException("项目时间线无效或片段过多");
        }
        return root;
    }

    public static String templatesToJson(List<ProjectRepository.EffectTemplateInfo> templates) throws JSONException {
        JSONArray out = new JSONArray();
        if (templates != null) {
            for (ProjectRepository.EffectTemplateInfo template : templates) {
                JSONObject value = new JSONObject();
                value.put("name", template.name());
                value.put("cue", template.cue());
                out.put(value);
            }
        }
        return out.toString(2);
    }

    public static List<TemplateDraft> templatesFromJson(String json) throws JSONException {
        List<TemplateDraft> out = new ArrayList<>();
        JSONArray array = new JSONArray(json);
        for (int i = 0; i < array.length(); i++) {
            JSONObject value = array.getJSONObject(i);
            out.add(new TemplateDraft(value.optString("name", ""), value.optString("cue", "")));
        }
        return out;
    }
}
